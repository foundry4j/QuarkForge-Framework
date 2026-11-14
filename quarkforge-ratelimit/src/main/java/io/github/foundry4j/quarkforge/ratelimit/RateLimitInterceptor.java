package io.github.foundry4j.quarkforge.ratelimit;

import io.github.foundry4j.quarkforge.core.exception.RateLimitException;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Method;

/// ## RateLimitInterceptor
/// [RateLimit] 注解的拦截器实现。限流键格式：
/// `rl:{类名}#{方法名}:{维度}:{维度值}`，保证不同接口额度互相独立。
@RateLimit(permits = Integer.MAX_VALUE)
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class RateLimitInterceptor {

    @Inject
    RateLimiter rateLimiter;

    @Inject
    SecurityIdentity identity;

    @Inject
    HttpServerRequest request;

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        RateLimit rateLimit = resolveBinding(context);
        String key = buildKey(rateLimit, context.getMethod());
        RateLimitResult result = rateLimiter.tryAcquire(
                key, rateLimit.permits(), rateLimit.windowSeconds(), rateLimit.algorithm());
        RateLimitHeaders.apply(request == null ? null : request.response(), result);
        if (!result.allowed()) {
            throw new RateLimitException();
        }
        return context.proceed();
    }

    private RateLimit resolveBinding(InvocationContext context) {
        RateLimit binding = context.getInterceptorBinding(RateLimit.class);
        if (binding != null) {
            return binding;
        }
        Method method = context.getMethod();
        RateLimit onMethod = method.getAnnotation(RateLimit.class);
        if (onMethod != null) {
            return onMethod;
        }
        return method.getDeclaringClass().getAnnotation(RateLimit.class);
    }

    private String buildKey(RateLimit rateLimit, Method method) {
        String base = "rl:" + method.getDeclaringClass().getName() + "#" + method.getName();
        return switch (rateLimit.key()) {
            case GLOBAL -> base + ":global";
            case USER -> base + ":user:" + currentUserKey();
            case IP -> base + ":ip:" + currentClientIp();
        };
    }

    private String currentUserKey() {
        if (identity != null && !identity.isAnonymous()) {
            return identity.getPrincipal().getName();
        }
        return currentClientIp();
    }

    private String currentClientIp() {
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host();
        }
        return "unknown";
    }
}
