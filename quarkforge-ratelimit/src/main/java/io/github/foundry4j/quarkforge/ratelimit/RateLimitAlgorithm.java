package io.github.foundry4j.quarkforge.ratelimit;

/// ## RateLimitAlgorithm
/// 限流算法：
///
/// - `FIXED_WINDOW`：固定窗口计数，实现最简单，但窗口临界点可能出现双倍突刺；
/// - `SLIDING_WINDOW`：滑动窗口（上一窗口计数加权 + 当前窗口计数），平滑临界点流量，
///   内存占用与固定窗口一致；
/// - `TOKEN_BUCKET`：令牌桶，按固定速率补充令牌，允许一定程度的突发流量。
public enum RateLimitAlgorithm {

    /// 固定窗口计数。
    FIXED_WINDOW,

    /// 滑动窗口计数。
    SLIDING_WINDOW,

    /// 令牌桶。
    TOKEN_BUCKET
}
