package io.github.foundry4j.quarkforge.gateway;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/// ## CompiledRoute
/// 启动时编译好的路由：上游地址解析一次，转发时只做字符串拼接。
///
/// @param id          路由标识
/// @param path        匹配前缀，已归一化（无尾部斜杠）
/// @param scheme      `http` 或 `https`
/// @param host        上游主机
/// @param port        上游端口
/// @param basePath    上游基础路径，可为空
/// @param stripPrefix 去掉匹配前缀后额外移除的路径段数
/// @param timeout     转发超时
public record CompiledRoute(String id, String path, String scheme, String host, int port,
                            String basePath, int stripPrefix, Duration timeout) {

    /// 请求路径是否命中该路由：等于前缀，或以 `前缀/` 开头；前缀为 `/` 时匹配所有路径。
    public boolean matches(String requestPath) {
        if ("/".equals(path)) {
            return requestPath.startsWith("/");
        }
        return requestPath.equals(path) || requestPath.startsWith(path + "/");
    }

    /// 计算转发给上游的 URI（含 query）。
    public String targetUri(String requestPath, String query) {
        String rest = requestPath.substring(path.length());
        rest = stripLeadingSegments(rest, stripPrefix);
        if (!rest.startsWith("/")) {
            rest = "/" + rest;
        }
        StringBuilder uri = new StringBuilder(basePath).append(rest);
        if (query != null && !query.isEmpty()) {
            uri.append('?').append(query);
        }
        return uri.toString();
    }

    static CompiledRoute parse(GatewayConfig.Route route, Duration defaultTimeout) {
        String path = normalizePath(route.path());
        URI upstream = URI.create(route.upstream() == null ? "" : route.upstream().trim());
        String scheme = upstream.getScheme() == null ? "http" : upstream.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("网关路由 " + route.id() + " 的 upstream 仅支持 http/https: " + route.upstream());
        }
        String host = upstream.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("网关路由 " + route.id() + " 的 upstream 缺少主机名: " + route.upstream());
        }
        int port = upstream.getPort() > 0 ? upstream.getPort() : ("https".equals(scheme) ? 443 : 80);
        String basePath = upstream.getRawPath() == null ? "" : upstream.getRawPath();
        while (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        Duration timeout = route.timeout().orElse(defaultTimeout);
        return new CompiledRoute(route.id(), path, scheme, host, port, basePath,
                Math.max(0, route.stripPrefix()), timeout);
    }

    /// 归一化匹配前缀：必须以 `/` 开头，去掉尾部斜杠（根路径除外）。
    static String normalizePath(String raw) {
        String path = raw == null ? "" : raw.trim();
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("网关路由 path 必须以 / 开头: " + raw);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /// 从以 `/` 开头的路径中移除前导段，返回剩余部分（可能为空串）。
    private static String stripLeadingSegments(String rest, int count) {
        String current = rest;
        for (int i = 0; i < count; i++) {
            if (current.isEmpty()) {
                return "";
            }
            int next = current.indexOf('/', 1);
            current = next < 0 ? "" : current.substring(next);
        }
        return current;
    }
}
