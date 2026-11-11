package io.github.foundry4j.quarkforge.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## GatewayRoutesTest
/// 验证路由编译、最长前缀匹配与前缀剥离逻辑。
class GatewayRoutesTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void matchesLongestPrefixFirst() {
        GatewayRoutes routes = new GatewayRoutes(List.of(
                route("api", "/api", "http://api:8080"),
                route("order", "/api/order", "http://order:8080")), DEFAULT_TIMEOUT);

        CompiledRoute matched = routes.match("/api/order/42").orElseThrow();

        assertEquals("order", matched.id());
    }

    @Test
    void keepsMatchedPrefixOutOfUpstreamUri() {
        GatewayRoutes routes = new GatewayRoutes(List.of(
                route("order", "/api/order", "http://order:8080")), DEFAULT_TIMEOUT);

        CompiledRoute matched = routes.match("/api/order/42/items").orElseThrow();

        assertEquals("/42/items?size=10", matched.targetUri("/api/order/42/items", "size=10"));
    }

    @Test
    void stripsExtraLeadingSegments() {
        GatewayRoutes routes = new GatewayRoutes(List.of(
                new RouteDef("order", "/api/order", "http://order:8080", 1, null, true)), DEFAULT_TIMEOUT);

        CompiledRoute matched = routes.match("/api/order/v1/42").orElseThrow();

        assertEquals("/42", matched.targetUri("/api/order/v1/42", null));
    }

    @Test
    void keepsUpstreamBasePath() {
        GatewayRoutes routes = new GatewayRoutes(List.of(
                route("order", "/api/order", "http://order:8080/base/")), DEFAULT_TIMEOUT);

        CompiledRoute matched = routes.match("/api/order/42").orElseThrow();

        assertEquals("/base/42", matched.targetUri("/api/order/42", null));
    }

    @Test
    void returnsEmptyWhenPathNotConfigured() {
        GatewayRoutes routes = new GatewayRoutes(List.of(
                route("order", "/api/order", "http://order:8080")), DEFAULT_TIMEOUT);

        assertTrue(routes.match("/api/user/1").isEmpty());
        assertTrue(routes.match("/api/orderX").isEmpty());
    }

    private static RouteDef route(String id, String path, String upstream) {
        return new RouteDef(id, path, upstream, 0, null, true);
    }

    /// 测试用路由定义。
    private record RouteDef(String id, String path, String upstream, int stripPrefix,
                            Duration timeoutValue, boolean enabled) implements GatewayConfig.Route {

        @Override
        public Optional<Duration> timeout() {
            return Optional.ofNullable(timeoutValue);
        }
    }
}
