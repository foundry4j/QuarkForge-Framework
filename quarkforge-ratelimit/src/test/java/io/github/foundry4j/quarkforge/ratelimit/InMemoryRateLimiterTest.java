package io.github.foundry4j.quarkforge.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static io.github.foundry4j.quarkforge.ratelimit.RateLimitAlgorithm.FIXED_WINDOW;
import static io.github.foundry4j.quarkforge.ratelimit.RateLimitAlgorithm.SLIDING_WINDOW;
import static io.github.foundry4j.quarkforge.ratelimit.RateLimitAlgorithm.TOKEN_BUCKET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## InMemoryRateLimiterTest
/// 使用可控时钟验证固定窗口、滑动窗口与令牌桶的判定结果。
class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);

    @Test
    void fixedWindowAllowsWithinPermits() {
        assertTrue(limiter.tryAcquire("a", 3, 60, FIXED_WINDOW).allowed());
        assertTrue(limiter.tryAcquire("a", 3, 60, FIXED_WINDOW).allowed());
        RateLimitResult last = limiter.tryAcquire("a", 3, 60, FIXED_WINDOW);
        assertTrue(last.allowed());
        assertEquals(0, last.remaining());
        assertEquals(3, last.limit());

        RateLimitResult rejected = limiter.tryAcquire("a", 3, 60, FIXED_WINDOW);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterSeconds() > 0);
    }

    @Test
    void fixedWindowResetsAfterWindow() {
        assertTrue(limiter.tryAcquire("a", 1, 60, FIXED_WINDOW).allowed());
        assertFalse(limiter.tryAcquire("a", 1, 60, FIXED_WINDOW).allowed());

        now.addAndGet(60_000);

        assertTrue(limiter.tryAcquire("a", 1, 60, FIXED_WINDOW).allowed());
    }

    @Test
    void differentKeysHaveIndependentQuotas() {
        assertTrue(limiter.tryAcquire("a", 1, 60, FIXED_WINDOW).allowed());
        assertFalse(limiter.tryAcquire("a", 1, 60, FIXED_WINDOW).allowed());
        assertTrue(limiter.tryAcquire("b", 1, 60, FIXED_WINDOW).allowed());
    }

    @Test
    void slidingWindowSmoothsBoundaryBurst() {
        alignToWindow();

        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.tryAcquire("s", 4, 1, SLIDING_WINDOW).allowed());
        }
        assertFalse(limiter.tryAcquire("s", 4, 1, SLIDING_WINDOW).allowed());

        // 进入下一窗口：上一窗口计数权重为 1，估算仍超限，不会出现固定窗口式的双倍突刺
        now.addAndGet(1_000);
        assertFalse(limiter.tryAcquire("s", 4, 1, SLIDING_WINDOW).allowed());

        // 窗口推进到 40% 位置：上一窗口 5 次（含被拒的一次）权重 0.4，5 * 0.4 + 2 = 4，恢复放行
        now.addAndGet(600);
        assertTrue(limiter.tryAcquire("s", 4, 1, SLIDING_WINDOW).allowed());
    }

    @Test
    void tokenBucketAllowsBurstAndRefills() {
        assertTrue(limiter.tryAcquire("b", 2, 1, TOKEN_BUCKET).allowed());
        assertTrue(limiter.tryAcquire("b", 2, 1, TOKEN_BUCKET).allowed());

        RateLimitResult rejected = limiter.tryAcquire("b", 2, 1, TOKEN_BUCKET);
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
        assertEquals(1, rejected.retryAfterSeconds());

        // 速率 2 个/秒，500ms 补 1 个令牌
        now.addAndGet(500);
        assertTrue(limiter.tryAcquire("b", 2, 1, TOKEN_BUCKET).allowed());
        assertFalse(limiter.tryAcquire("b", 2, 1, TOKEN_BUCKET).allowed());
    }

    private void alignToWindow() {
        long current = now.get();
        now.set(current - current % 1_000);
    }
}
