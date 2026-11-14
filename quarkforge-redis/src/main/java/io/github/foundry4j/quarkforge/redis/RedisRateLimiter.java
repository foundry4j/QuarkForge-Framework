package io.github.foundry4j.quarkforge.redis;

import io.github.foundry4j.quarkforge.ratelimit.RateLimitAlgorithm;
import io.github.foundry4j.quarkforge.ratelimit.RateLimitResult;
import io.github.foundry4j.quarkforge.ratelimit.RateLimiter;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.RedisKeyNotFoundException;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/// ## RedisRateLimiter
/// 基于 Redis 的分布式限流实现，支持 [RateLimitAlgorithm] 的三种算法：
///
/// - 固定窗口：`INCR` + `EXPIRE`；
/// - 滑动窗口：当前窗口与上一窗口两个计数器，按剩余时间加权；
/// - 令牌桶：Lua 脚本原子地读取、补充、扣减令牌，避免并发竞争。
///
/// 普通 CDI Bean，优先级高于 ratelimit 模块中以 `@DefaultBean` 声明的内存实现，
/// 引入本模块后自动生效，无需额外配置。
@ApplicationScoped
public class RedisRateLimiter implements RateLimiter {

    /// 令牌桶的原子操作脚本，返回 `{allowed, tokens}`。
    private static final String TOKEN_BUCKET_SCRIPT = """
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            local ts = tonumber(redis.call('HGET', KEYS[1], 'ts'))
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', now)
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return {allowed, tostring(tokens)}
            """;

    private final RedisDataSource dataSource;
    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;

    @Inject
    public RedisRateLimiter(RedisDataSource dataSource) {
        this.dataSource = dataSource;
        this.values = dataSource.value(String.class);
        this.keys = dataSource.key();
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits, long windowSeconds, RateLimitAlgorithm algorithm) {
        long now = System.currentTimeMillis();
        return switch (algorithm) {
            case FIXED_WINDOW -> fixedWindow(key, permits, windowSeconds, now);
            case SLIDING_WINDOW -> slidingWindow(key, permits, windowSeconds, now);
            case TOKEN_BUCKET -> tokenBucket(key, permits, windowSeconds, now);
        };
    }

    private RateLimitResult fixedWindow(String key, int permits, long windowSeconds, long now) {
        Long incremented = values.incr(key);
        long count = incremented == null ? 0 : incremented;
        if (count == 1L) {
            keys.expire(key, windowSeconds);
        }
        long ttl = remainingTtl(key, windowSeconds);
        boolean allowed = count <= permits;
        return new RateLimitResult(allowed, permits, Math.max(0, permits - count),
                now / 1000 + ttl, allowed ? 0 : ttl);
    }

    private RateLimitResult slidingWindow(String key, int permits, long windowSeconds, long now) {
        long windowMillis = windowSeconds * 1000;
        long bucket = now / windowMillis;
        String currentKey = key + ":" + bucket;
        String previousKey = key + ":" + (bucket - 1);

        Long incremented = values.incr(currentKey);
        long current = incremented == null ? 0 : incremented;
        if (current == 1L) {
            // 保留两个窗口，下一窗口内仍可读到本窗口计数
            keys.expire(currentKey, windowSeconds * 2);
        }
        String previousRaw = values.get(previousKey);
        long previous = previousRaw == null ? 0 : Long.parseLong(previousRaw);

        double weight = (windowMillis - now % windowMillis) / (double) windowMillis;
        double estimated = previous * weight + current;
        boolean allowed = estimated <= permits;
        long reset = ((bucket + 1) * windowMillis + 999) / 1000;
        long retry = allowed ? 0 : Math.max(1, reset - now / 1000);
        return new RateLimitResult(allowed, permits,
                Math.max(0, (long) Math.floor(permits - estimated)), reset, retry);
    }

    private RateLimitResult tokenBucket(String key, int permits, long windowSeconds, long now) {
        double ratePerMilli = permits / (windowSeconds * 1000.0);
        long ttlMillis = windowSeconds * 2000;
        var response = dataSource.execute("EVAL", TOKEN_BUCKET_SCRIPT, "1", key,
                String.valueOf(permits), String.valueOf(ratePerMilli),
                String.valueOf(now), String.valueOf(ttlMillis));

        boolean allowed = response.get(0).toLong() == 1L;
        double tokens = Double.parseDouble(response.get(1).toString());
        long reset = now / 1000 + (long) Math.ceil((permits - tokens) / (ratePerMilli * 1000));
        long retry = allowed
                ? 0 : Math.max(1, (long) Math.ceil((1.0 - tokens) / (ratePerMilli * 1000)));
        return new RateLimitResult(allowed, permits, (long) Math.floor(tokens), reset, retry);
    }

    private long remainingTtl(String key, long windowSeconds) {
        try {
            long ttl = keys.ttl(key);
            return ttl > 0 ? ttl : windowSeconds;
        } catch (RedisKeyNotFoundException e) {
            return windowSeconds;
        }
    }
}
