package io.github.foundry4j.quarkforge.ratelimit;

import io.vertx.core.http.HttpServerResponse;

import java.util.function.BiConsumer;

/// ## RateLimitHeaders
/// 把 [RateLimitResult] 写入标准限流响应头：
///
/// - `X-RateLimit-Limit`：额度上限
/// - `X-RateLimit-Remaining`：剩余额度
/// - `X-RateLimit-Reset`：额度重置时间（epoch 秒）
/// - `Retry-After`：仅在被拒绝时写入，建议等待秒数
final class RateLimitHeaders {

    static final String LIMIT = "X-RateLimit-Limit";
    static final String REMAINING = "X-RateLimit-Remaining";
    static final String RESET = "X-RateLimit-Reset";
    static final String RETRY_AFTER = "Retry-After";

    private RateLimitHeaders() {
    }

    static void apply(HttpServerResponse response, RateLimitResult result) {
        if (response != null) {
            apply(result, response::putHeader);
        }
    }

    /// 与具体响应类型解耦，便于测试。
    static void apply(RateLimitResult result, BiConsumer<String, String> headers) {
        headers.accept(LIMIT, String.valueOf(result.limit()));
        headers.accept(REMAINING, String.valueOf(result.remaining()));
        headers.accept(RESET, String.valueOf(result.resetEpochSecond()));
        if (!result.allowed()) {
            headers.accept(RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        }
    }
}
