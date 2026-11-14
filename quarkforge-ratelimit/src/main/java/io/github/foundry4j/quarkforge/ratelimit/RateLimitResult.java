package io.github.foundry4j.quarkforge.ratelimit;

/// ## RateLimitResult
/// 一次限流判定的结果，同时携带响应头所需的信息：
///
/// - `limit`：窗口/桶内的总额度，对应 `X-RateLimit-Limit`
/// - `remaining`：剩余额度，对应 `X-RateLimit-Remaining`
/// - `resetEpochSecond`：额度重置时间（epoch 秒），对应 `X-RateLimit-Reset`
/// - `retryAfterSeconds`：被拒绝时建议的等待秒数，对应 `Retry-After`
///
/// @param allowed           是否放行
/// @param limit             总额度
/// @param remaining         剩余额度
/// @param resetEpochSecond  额度重置时间（epoch 秒）
/// @param retryAfterSeconds 建议等待秒数，放行时为 0
public record RateLimitResult(boolean allowed, long limit, long remaining,
                              long resetEpochSecond, long retryAfterSeconds) {
}
