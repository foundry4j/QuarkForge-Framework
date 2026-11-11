package io.github.foundry4j.quarkforge.gateway;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// ## GatewayProxyTest
/// 用一个本地 Vert.x 桩服务验证转发链路：方法/路径/查询串、请求头过滤与请求体。
class GatewayProxyTest {

    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicReference<String> receivedConnection = new AtomicReference<>();

    private Vertx vertx;
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(request -> request.bodyHandler(body -> {
            receivedMethod.set(request.method().name());
            receivedPath.set(request.path());
            receivedQuery.set(request.query());
            receivedToken.set(request.getHeader("X-Token"));
            receivedConnection.set(request.getHeader("Connection"));
            if ("/slow".equals(request.path())) {
                vertx.setTimer(500, ignored -> request.response().end("late"));
                return;
            }
            if ("/echo".equals(request.path())) {
                request.response().putHeader("Content-Type", "application/json").end(body);
                return;
            }
            request.response().setStatusCode(201).putHeader("X-Upstream", "yes").end("ok");
        }));
        port = server.listen(0).toCompletionStage().toCompletableFuture().get().actualPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get();
        vertx.close().toCompletionStage().toCompletableFuture().get();
    }

    @Test
    void forwardsMethodHeadersQueryAndResponse() throws Exception {
        GatewayProxy proxy = new GatewayProxy(vertx);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("X-Token", "t1")
                .add("Connection", "keep-alive");

        GatewayProxy.UpstreamResponse response = proxy
                .forward(route("/api/order", Duration.ofSeconds(2)), "GET", "/42?size=10", headers, null)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(201, response.status());
        assertEquals("ok", response.body().toString());
        assertEquals("yes", response.headers().get("X-Upstream"));
        assertEquals("GET", receivedMethod.get());
        assertEquals("/42", receivedPath.get());
        assertEquals("size=10", receivedQuery.get());
        assertEquals("t1", receivedToken.get());
        assertNull(receivedConnection.get());
    }

    @Test
    void forwardsRequestBody() throws Exception {
        GatewayProxy proxy = new GatewayProxy(vertx);
        Buffer body = Buffer.buffer("{\"orderNo\":\"A001\"}");

        GatewayProxy.UpstreamResponse response = proxy
                .forward(route("/", Duration.ofSeconds(2)), "POST", "/echo",
                        MultiMap.caseInsensitiveMultiMap(), body)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(200, response.status());
        assertEquals("{\"orderNo\":\"A001\"}", response.body().toString());
        assertEquals("POST", receivedMethod.get());
    }

    @Test
    void failsWhenUpstreamDoesNotRespondInTime() {
        GatewayProxy proxy = new GatewayProxy(vertx);

        assertThrows(ExecutionException.class, () -> proxy
                .forward(route("/slow", Duration.ofMillis(50)), "GET", "/slow",
                        MultiMap.caseInsensitiveMultiMap(), null)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void failsWhenUpstreamUnreachable() {
        GatewayProxy proxy = new GatewayProxy(vertx);
        CompiledRoute unreachable = new CompiledRoute("down", "/down", "http", "localhost", 1,
                "", 0, Duration.ofSeconds(1));

        assertThrows(ExecutionException.class, () -> proxy
                .forward(unreachable, "GET", "/down", MultiMap.caseInsensitiveMultiMap(), null)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    private CompiledRoute route(String path, Duration timeout) {
        return new CompiledRoute("stub", path, "http", "localhost", port, "", 0, timeout);
    }
}
