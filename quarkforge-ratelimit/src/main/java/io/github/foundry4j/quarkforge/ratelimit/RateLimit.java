package io.github.foundry4j.quarkforge.ratelimit;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// ## RateLimit
/// 声明式限流注解，标注在资源方法或类上：
///
/// ```java
/// @POST
/// @RateLimit(permits = 5, windowSeconds = 60, key = KeyStrategy.USER,
///         algorithm = RateLimitAlgorithm.SLIDING_WINDOW)
/// public Result<Void> sendSmsCode() { ... }
/// ```
///
/// 超出额度时抛出 `RateLimitException`，由 web 层映射为 HTTP 429；
/// 每次判定都会写入 `X-RateLimit-Limit/Remaining/Reset`，被拒绝时额外写入 `Retry-After`。
@Inherited
@Documented
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /// 时间窗口内允许的最大请求数。
    @Nonbinding
    int permits();

    /// 时间窗口长度（秒），默认 60 秒。
    @Nonbinding
    long windowSeconds() default 60;

    /// 限流维度，默认按登录用户。
    @Nonbinding
    KeyStrategy key() default KeyStrategy.USER;

    /// 限流算法，默认固定窗口；临界点流量较大时建议使用滑动窗口或令牌桶。
    @Nonbinding
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.FIXED_WINDOW;
}
