package io.github.foundry4j.quarkforge.ratelimit;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/// ## InMemoryRateLimiter
/// 单机内存限流实现，支持 [RateLimitAlgorithm] 的三种算法：
///
/// - 固定窗口：每个 key 一个计数器，窗口过期后清零；
/// - 滑动窗口：上一窗口计数按剩余时间加权，避免临界点双倍突刺；
/// - 令牌桶：容量为 `permits`，按 `permits / windowSeconds` 的速率补充，允许突发。
///
/// 过期状态在访问时惰性回收，条目超过阈值时集中清理。多实例部署请引入 `quarkforge-redis`。
@DefaultBean
@ApplicationScoped
public class InMemoryRateLimiter implements RateLimiter {

    /// 触发集中清理的条目数阈值。
    private static final int PURGE_THRESHOLD = 10_000;

    private final Map<String, FixedWindow> fixedWindows = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> slidingWindows = new ConcurrentHashMap<>();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryRateLimiter() {
        this(System::currentTimeMillis);
    }

    /// 供测试注入可控时钟。
    InMemoryRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits, long windowSeconds, RateLimitAlgorithm algorithm) {
        long now = clock.getAsLong();
        purgeIfNeeded(now);
        return switch (algorithm) {
            case FIXED_WINDOW -> fixedWindow(key, permits, windowSeconds, now);
            case SLIDING_WINDOW -> slidingWindow(key, permits, windowSeconds, now);
            case TOKEN_BUCKET -> tokenBucket(key, permits, windowSeconds, now);
        };
    }

    private RateLimitResult fixedWindow(String key, int permits, long windowSeconds, long now) {
        long windowMillis = windowSeconds * 1000;
        FixedWindow window = fixedWindows.compute(key, (_, current) -> {
            if (current == null || now >= current.endMillis) {
                return new FixedWindow(now + windowMillis, 1);
            }
            current.count++;
            return current;
        });
        boolean allowed = window.count <= permits;
        long retry = allowed ? 0 : secondsUntil(window.endMillis, now);
        return new RateLimitResult(allowed, permits, Math.max(0, permits - window.count),
                epochSecond(window.endMillis), retry);
    }

    private RateLimitResult slidingWindow(String key, int permits, long windowSeconds, long now) {
        long windowMillis = windowSeconds * 1000;
        SlidingWindow window = slidingWindows.compute(key, (_, current) -> {
            long start = now - now % windowMillis;
            long end = start + windowMillis;
            if (current == null || now >= current.endMillis) {
                // 上一窗口恰好是紧邻的窗口时才纳入加权，跨过多个窗口则视为 0
                long previous = current != null && current.endMillis == start ? current.count : 0;
                return new SlidingWindow(end, windowMillis, previous, 1);
            }
            current.count++;
            return current;
        });
        double weight = (window.endMillis - now) / (double) windowMillis;
        double estimated = window.previousCount * weight + window.count;
        boolean allowed = estimated <= permits;
        long retry = allowed ? 0 : secondsUntil(window.endMillis, now);
        return new RateLimitResult(allowed, permits,
                Math.max(0, (long) Math.floor(permits - estimated)),
                epochSecond(window.endMillis), retry);
    }

    private RateLimitResult tokenBucket(String key, int permits, long windowSeconds, long now) {
        double capacity = permits;
        double ratePerMilli = capacity / (windowSeconds * 1000.0);
        Bucket bucket = buckets.compute(key, (_, current) -> {
            Bucket value = current == null ? new Bucket(capacity, now) : current;
            value.tokens = Math.min(capacity,
                    value.tokens + Math.max(0, now - value.lastRefillMillis) * ratePerMilli);
            value.lastRefillMillis = now;
            value.allowed = value.tokens >= 1.0;
            if (value.allowed) {
                value.tokens -= 1.0;
            }
            return value;
        });
        long retry = bucket.allowed
                ? 0 : Math.max(1, (long) Math.ceil((1.0 - bucket.tokens) / (ratePerMilli * 1000)));
        long reset = now / 1000 + (long) Math.ceil((capacity - bucket.tokens) / (ratePerMilli * 1000));
        return new RateLimitResult(bucket.allowed, permits, (long) Math.floor(bucket.tokens), reset, retry);
    }

    private void purgeIfNeeded(long now) {
        if (fixedWindows.size() + slidingWindows.size() + buckets.size() < PURGE_THRESHOLD) {
            return;
        }
        fixedWindows.entrySet().removeIf(entry -> now >= entry.getValue().endMillis);
        slidingWindows.entrySet().removeIf(entry ->
                now >= entry.getValue().endMillis + entry.getValue().windowMillis);
        buckets.entrySet().removeIf(entry -> entry.getValue().tokens >= entry.getValue().capacity);
    }

    private static long epochSecond(long millis) {
        return (millis + 999) / 1000;
    }

    private static long secondsUntil(long endMillis, long now) {
        return Math.max(1, (endMillis - now + 999) / 1000);
    }

    private static final class FixedWindow {
        final long endMillis;
        int count;

        FixedWindow(long endMillis, int count) {
            this.endMillis = endMillis;
            this.count = count;
        }
    }

    private static final class SlidingWindow {
        final long endMillis;
        final long windowMillis;
        final long previousCount;
        int count;

        SlidingWindow(long endMillis, long windowMillis, long previousCount, int count) {
            this.endMillis = endMillis;
            this.windowMillis = windowMillis;
            this.previousCount = previousCount;
            this.count = count;
        }
    }

    private static final class Bucket {
        final double capacity;
        double tokens;
        long lastRefillMillis;
        boolean allowed;

        Bucket(double capacity, long now) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillMillis = now;
        }
    }
}
