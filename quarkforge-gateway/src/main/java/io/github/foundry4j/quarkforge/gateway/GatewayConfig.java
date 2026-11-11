package io.github.foundry4j.quarkforge.gateway;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/// ## GatewayConfig
/// 进程内网关配置，路由在启动时一次性注册，修改后需重启应用生效。
///
/// ```properties
/// quarkforge.gateway.enabled=true
/// quarkforge.gateway.default-timeout=5s
/// quarkforge.gateway.routes[0].id=order
/// quarkforge.gateway.routes[0].path=/api/order
/// quarkforge.gateway.routes[0].upstream=http://order-service:8080
/// quarkforge.gateway.routes[0].strip-prefix=0
/// quarkforge.gateway.routes[0].timeout=3s
/// ```
///
/// `path` 为请求路径前缀，匹配后默认去掉该前缀再拼到 `upstream`；
/// `strip-prefix` 表示在去掉 `path` 之后再额外移除的前导路径段数。
@ConfigMapping(prefix = "quarkforge.gateway")
public interface GatewayConfig {

    /// 是否启用网关。
    @WithDefault("true")
    boolean enabled();

    /// 路由未单独配置超时时的默认值。
    @WithDefault("5s")
    Duration defaultTimeout();

    /// 路由列表，按最长前缀优先匹配。
    List<Route> routes();

    /// 单条路由定义。
    interface Route {

        /// 路由标识，仅用于日志与排查。
        String id();

        /// 匹配的请求路径前缀，必须以 `/` 开头。
        String path();

        /// 上游服务地址，如 `http://order-service:8080`，可带基础路径。
        String upstream();

        /// 去掉 `path` 后额外移除的前导路径段数。
        @WithDefault("0")
        int stripPrefix();

        /// 该路由的转发超时，缺省使用 `quarkforge.gateway.default-timeout`。
        Optional<Duration> timeout();

        /// 是否启用该路由。
        @WithDefault("true")
        boolean enabled();
    }
}
