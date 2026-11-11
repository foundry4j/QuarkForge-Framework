package io.github.foundry4j.quarkforge.gateway;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.regex.Pattern;

/// ## GatewayRouter
/// 启动时把配置的路由挂到 Quarkus 的 Vert.x Router 上。
///
/// 每条路由注册两段处理器：先由 `BodyHandler` 聚合请求体，再交给 [GatewayProxy] 转发。
/// 路由按最长前缀优先注册，`/api/order` 会优先于 `/api` 命中。
@ApplicationScoped
public class GatewayRouter {

    private static final Logger LOG = Logger.getLogger(GatewayRouter.class);

    @Inject
    GatewayConfig config;

    @Inject
    GatewayRoutes routes;

    @Inject
    GatewayProxy proxy;

    void init(@Observes Router router) {
        if (!config.enabled()) {
            LOG.info("网关未启用（quarkforge.gateway.enabled=false）");
            return;
        }
        if (routes.routes().isEmpty()) {
            LOG.info("网关未配置任何路由");
            return;
        }
        for (CompiledRoute route : routes.routes()) {
            // 精确匹配前缀本身，以及其下的所有子路径
            String regex = Pattern.quote(route.path()) + "(/.*)?";
            router.routeWithRegex(regex)
                    .handler(BodyHandler.create())
                    .handler(ctx -> proxy.handle(ctx, route));
            LOG.infof("网关路由已注册 %s: %s -> %s://%s:%d%s",
                    route.id(), route.path(), route.scheme(), route.host(), route.port(), route.basePath());
        }
    }
}
