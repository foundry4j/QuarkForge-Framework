package io.github.foundry4j.quarkforge.gateway;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// ## GatewayProxy
/// 把命中的请求转发到上游并原样回写响应。
///
/// 行为约定：
///
/// - 请求头逐项透传，剥离 `Host`/`Content-Length`/`Connection` 等逐跳头；
/// - 不自动跟随重定向、不自动解压，保持上游响应原样；
/// - 超时返回 `504`，上游不可达返回 `502`，响应体沿用 [io.github.foundry4j.quarkforge.core.api.Result] 结构；
/// - 请求与响应体整体缓冲在内存中，大文件上传下载场景请勿直接走本模块。
@ApplicationScoped
public class GatewayProxy {

    private static final Logger LOG = Logger.getLogger(GatewayProxy.class);

    /// 逐跳头：只对单次连接有意义，代理不应透传。
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");

    private static final int CONNECT_TIMEOUT_MS = 3_000;

    private final Map<Boolean, WebClient> clients = new ConcurrentHashMap<>();

    @Inject
    Vertx vertx;

    public GatewayProxy() {
    }

    GatewayProxy(Vertx vertx) {
        this.vertx = vertx;
    }

    /// 处理命中的路由：读取请求体 → 转发 → 回写响应或错误。
    void handle(RoutingContext ctx, CompiledRoute route) {
        Buffer body = ctx.body() == null ? null : ctx.body().buffer();
        String uri = route.targetUri(ctx.request().path(), ctx.request().query());
        forward(route, ctx.request().method().name(), uri, ctx.request().headers(), body)
                .onSuccess(upstream -> write(ctx, upstream))
                .onFailure(error -> fail(ctx, route, error));
    }

    /// 执行一次转发，返回上游状态码、响应头与响应体。
    Future<UpstreamResponse> forward(CompiledRoute route, String method, String uri,
                                     MultiMap headers, Buffer body) {
        Promise<UpstreamResponse> promise = Promise.promise();
        HttpRequest<Buffer> request = client("https".equals(route.scheme()))
                .request(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), route.port(), route.host(), uri);
        for (String name : headers.names()) {
            if (isHopByHop(name)) {
                continue;
            }
            for (String value : headers.getAll(name)) {
                request.putHeader(name, value);
            }
        }
        long timeoutMs = route.timeout() == null ? 5_000 : Math.max(1, route.timeout().toMillis());
        long timer = vertx.setTimer(timeoutMs, ignored -> promise.tryFail(new UpstreamTimeoutException(timeoutMs)));
        Future<HttpResponse<Buffer>> sent = body != null && body.length() > 0
                ? request.sendBuffer(body)
                : request.send();
        sent.onComplete(result -> {
            vertx.cancelTimer(timer);
            if (result.succeeded()) {
                HttpResponse<Buffer> response = result.result();
                promise.tryComplete(new UpstreamResponse(response.statusCode(), response.headers(), response.body()));
            } else {
                promise.tryFail(result.cause());
            }
        });
        return promise.future();
    }

    private void write(RoutingContext ctx, UpstreamResponse upstream) {
        HttpServerResponse response = ctx.response().setStatusCode(upstream.status());
        for (String name : upstream.headers().names()) {
            if (isHopByHop(name)) {
                continue;
            }
            response.putHeader(name, upstream.headers().getAll(name));
        }
        if (upstream.body() == null || upstream.body().length() == 0) {
            response.end();
        } else {
            response.end(upstream.body());
        }
    }

    private void fail(RoutingContext ctx, CompiledRoute route, Throwable error) {
        boolean timeout = error instanceof UpstreamTimeoutException;
        LOG.warnf(error, "网关转发失败 route=%s upstream=%s://%s:%d timeout=%s",
                route.id(), route.scheme(), route.host(), route.port(), timeout);
        if (ctx.response().ended()) {
            return;
        }
        int status = timeout ? 504 : 502;
        CommonErrorCode code = timeout ? CommonErrorCode.GATEWAY_TIMEOUT : CommonErrorCode.SERVICE_UNAVAILABLE;
        ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(errorBody(code.code(), timeout ? code.message() : "上游服务不可达"));
    }

    /// 与 `Result` 结构保持一致，避免下游包装逻辑在网关层缺失。
    private static String errorBody(int code, String message) {
        return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                + System.currentTimeMillis() + "}";
    }

    private WebClient client(boolean ssl) {
        return clients.computeIfAbsent(ssl, ignored -> {
            WebClientOptions options = new WebClientOptions()
                    .setSsl(ssl)
                    .setFollowRedirects(false)
                    .setConnectTimeout(CONNECT_TIMEOUT_MS);
            // 保持上游原始字节，避免解压后 Content-Encoding 与响应体不一致
            options.setDecompressionSupported(false);
            return WebClient.create(vertx, options);
        });
    }

    private static boolean isHopByHop(String name) {
        return HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT));
    }

    /// 上游响应快照。
    public record UpstreamResponse(int status, MultiMap headers, Buffer body) {
    }

    /// 转发超时标记，用于区分 504 与其他转发失败（502）。
    private static final class UpstreamTimeoutException extends RuntimeException {

        UpstreamTimeoutException(long timeoutMs) {
            super("上游服务响应超时: " + timeoutMs + "ms");
        }
    }
}
