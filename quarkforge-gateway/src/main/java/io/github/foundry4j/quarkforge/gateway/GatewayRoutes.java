package io.github.foundry4j.quarkforge.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// ## GatewayRoutes
/// 启动时把配置编译成路由表，并按最长前缀优先排序；请求进来时返回命中的路由。
@ApplicationScoped
public class GatewayRoutes {

    private static final Logger LOG = Logger.getLogger(GatewayRoutes.class);

    private final List<CompiledRoute> routes;

    @Inject
    public GatewayRoutes(GatewayConfig config) {
        this(config.routes(), config.defaultTimeout());
    }

    GatewayRoutes(List<GatewayConfig.Route> definitions, Duration defaultTimeout) {
        List<CompiledRoute> compiled = new ArrayList<>();
        for (GatewayConfig.Route definition : definitions) {
            if (!definition.enabled()) {
                continue;
            }
            compiled.add(CompiledRoute.parse(definition, defaultTimeout));
        }
        compiled.sort(Comparator.comparingInt((CompiledRoute route) -> route.path().length()).reversed());
        this.routes = List.copyOf(compiled);
        LOG.infof("网关路由已加载 %d 条", routes.size());
    }

    /// 已编译并排序（最长前缀优先）的路由。
    public List<CompiledRoute> routes() {
        return routes;
    }

    /// 返回命中请求路径的路由，未命中返回空。
    public Optional<CompiledRoute> match(String requestPath) {
        for (CompiledRoute route : routes) {
            if (route.matches(requestPath)) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }
}
