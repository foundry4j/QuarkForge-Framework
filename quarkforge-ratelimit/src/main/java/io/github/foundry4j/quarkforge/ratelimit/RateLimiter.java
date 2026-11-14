package io.github.foundry4j.quarkforge.ratelimit;

/// ## RateLimiter
/// 限流后端 SPI。默认实现为基于内存的 [InMemoryRateLimiter]（支持固定窗口、滑动窗口、令牌桶）；
/// 引入 `quarkforge-redis` 模块后自动切换为 Redis 分布式限流实现。
public interface RateLimiter {

    /// 尝试获取一次调用许可。
    ///
    /// @param key           限流键（已含方法与维度信息）
    /// @param permits       窗口内允许的请求数（令牌桶容量）
    /// @param windowSeconds 窗口长度（秒）
    /// @param algorithm     限流算法
    /// @return 判定结果，包含剩余额度与重置时间，用于生成 `X-RateLimit-*` 响应头
    RateLimitResult tryAcquire(String key, int permits, long windowSeconds, RateLimitAlgorithm algorithm);
}
