package io.github.foundry4j.quarkforge.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.github.foundry4j.quarkforge.cache.annotation.CacheEvict;
import io.github.foundry4j.quarkforge.cache.annotation.CacheInterceptor;
import io.github.foundry4j.quarkforge.cache.annotation.Cacheable;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/// ## LocalCacheService
/// Caffeine 本地缓存实现，单机零依赖，默认存活 5 分钟、最多 10000 条。
///
/// ### 穿透 / 击穿防护
///
/// 引入了注解式增强后（[Cacheable] / [CacheEvict]），缓存自动具备：
///
/// - **布隆过滤器**：首次 Bloom 判定「一定不存在」则跳过下游查询
/// - **single-flight**：同键并发只加载一次
/// - **空值缓存**：loader 返回 `null` 时以较短 TTL 写入哨兵值，阻断穿透
///
/// 防护入口是 [computeProtected]，由 [CacheInterceptor] 调用；
/// 业务代码直接调用 [CacheService.getOrLoad] 仍保持原有行为。
///
/// ```properties
/// quarkforge.cache.local.max-size=10000
/// quarkforge.cache.local.default-ttl=PT5M
/// quarkforge.cache.local.bloom.enabled=true
/// quarkforge.cache.local.bloom.expected-insertions=100000
/// quarkforge.cache.local.bloom.false-positive-rate=0.01
/// quarkforge.cache.local.null-value-ttl=PT1M
/// quarkforge.cache.ttl-jitter=0.1        # 过期时间随机上浮 0~10%，防止同时过期
/// ```
///
/// 引入 `quarkforge-redis` 后会被 [io.github.foundry4j.quarkforge.redis.RedisTwoLevelCacheService]
/// 覆盖，业务代码仍然只依赖 [CacheService]。
@DefaultBean
@ApplicationScoped
public class LocalCacheService implements CacheService {

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.max-size", defaultValue = "10000")
    long maxSize;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.default-ttl", defaultValue = "PT5M")
    Duration defaultTtl;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.bloom.enabled", defaultValue = "true")
    boolean bloomEnabled;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.bloom.expected-insertions", defaultValue = "100000")
    int bloomExpectedInsertions;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.bloom.false-positive-rate", defaultValue = "0.01")
    double bloomFalsePositiveRate;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.local.null-value-ttl", defaultValue = "PT1M")
    Duration nullValueTtl;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.ttl-jitter", defaultValue = "0.1")
    double ttlJitter;

    private Cache<String, CachedValue> cache;
    private SimpleBloomFilter bloom;
    private final SingleFlightGate gate = new SingleFlightGate();

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new Expiry<String, CachedValue>() {
                    @Override
                    public long expireAfterCreate(String key, CachedValue value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedValue value, long currentTime,
                                                  long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, CachedValue value, long currentTime,
                                                long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        if (bloomEnabled) {
            bloom = new SimpleBloomFilter(bloomExpectedInsertions, bloomFalsePositiveRate);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type, Duration ttl) {
        CachedValue cached = cache.getIfPresent(key);
        if (cached == null) {
            return null;
        }
        if (NullValue.isNull(cached.value())) {
            return null;
        }
        return type.cast(cached.value());
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        if (key == null) {
            return;
        }
        Object toStore = value == null ? NullValue.INSTANCE : value;
        cache.put(key, new CachedValue(toStore, ttlNanos(ttl)));
        registerBloom(key);
    }

    @Override
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type, ttl);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded, ttl);
        }
        return loaded;
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    // ---------- 注解式防护入口 ----------

    /// **增强版** getOrLoad：具备 bloom + single-flight + 空值缓存三重防护。
    ///
    /// - Bloom 判定 key「一定不存在」时，跳过缓存查询直接走 single-flight
    /// - 并发同键只调用一次 loader
    /// - loader 返回 null 时以 `nullValueTtl` 写入哨兵值，阻断穿透
    @SuppressWarnings("unchecked")
    public <T> T computeProtected(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        // 1. bloom 快速过滤
        if (bloom != null && !bloom.mightContain(key)) {
            return withSingleFlight(key, type, ttl, loader);
        }

        // 2. 正常缓存查询
        T cached = get(key, type, ttl);
        if (cached != null) {
            return cached;
        }
        // 哨兵值命中：返回 null（表示「这是一个空」，而不是穿透）
        CachedValue sentinel = cache.getIfPresent(key);
        if (sentinel != null && NullValue.isNull(sentinel.value())) {
            return null;
        }

        // 3. single-flight + loader
        return withSingleFlight(key, type, ttl, loader);
    }

    private <T> T withSingleFlight(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        SingleFlightGate.Registration reg = gate.tryRegister(key);
        CompletableFuture<Object> future = reg.future();
        if (reg.iAmRegistrant()) {
            Object loaded;
            try {
                loaded = loader.get();
            } catch (RuntimeException | Error e) {
                gate.fail(key, e);
                throw e;
            }
            Object toStore = loaded == null ? NullValue.INSTANCE : loaded;
            cache.put(key, new CachedValue(toStore,
                    loaded == null ? ttlNanos(nullValueTtl) : ttlNanos(ttl)));
            registerBloom(key);
            gate.complete(key, toStore);
            if (NullValue.isNull(toStore)) {
                return null;
            }
            return type.cast(toStore);
        }
        // 等待已在进行中的 future
        Object result;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw new RuntimeException(e.getCause());
        }
        if (NullValue.isNull(result)) {
            return null;
        }
        return type.cast(result);
    }

    private <T> T loadAndStore(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T loaded = loader.get();
        Object toStore = loaded == null ? NullValue.INSTANCE : loaded;
        cache.put(key, new CachedValue(toStore,
                loaded == null ? ttlNanos(nullValueTtl) : ttlNanos(ttl)));
        registerBloom(key);
        return loaded;
    }

    private void registerBloom(String key) {
        if (bloom != null) {
            bloom.put(key);
        }
    }

    public SingleFlightGate singleFlight() {
        return gate;
    }

    public SimpleBloomFilter bloom() {
        return bloom;
    }

    public boolean bloomEnabled() {
        return bloom != null;
    }

    /// 当前缓存条目数（会先清理过期条目）。
    public long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private long ttlNanos(Duration ttl) {
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? defaultTtl : ttl;
        return Math.max(TtlJitter.apply(effective, ttlJitter).toNanos(), 0);
    }

    private record CachedValue(Object value, long ttlNanos) {
    }
}
