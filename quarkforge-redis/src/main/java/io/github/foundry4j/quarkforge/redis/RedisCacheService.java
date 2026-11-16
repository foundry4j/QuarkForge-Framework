package io.github.foundry4j.quarkforge.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.foundry4j.quarkforge.cache.TtlJitter;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.function.Supplier;

/// ## RedisCacheService
/// 基于 JSON 序列化的缓存服务，内置 cache-aside（旁路缓存）模式：
///
/// ```java
/// User user = cache.getOrLoad("user:" + id, User.class, Duration.ofMinutes(10),
///         () -> userRepository.requireById(id));
/// ```
///
/// 使用前配置连接：`quarkus.redis.hosts=redis://localhost:6379`。
@ApplicationScoped
public class RedisCacheService {

    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;
    private final ObjectMapper objectMapper;

    @Inject
    @ConfigProperty(name = "quarkforge.cache.ttl-jitter", defaultValue = "0.1")
    double ttlJitter;

    @Inject
    public RedisCacheService(RedisDataSource dataSource, ObjectMapper objectMapper) {
        this.values = dataSource.value(String.class);
        this.keys = dataSource.key();
        this.objectMapper = objectMapper;
    }

    /// 写入缓存并设置过期时间（叠加随机抖动，避免批量键同时过期）。
    public <T> void set(String key, T value, Duration ttl) {
        values.setex(key, Math.max(TtlJitter.apply(ttl, ttlJitter).toSeconds(), 1), toJson(value));
    }

    /// 读取缓存，未命中或反序列化失败时返回 `null`。
    public <T> T get(String key, Class<T> type) {
        String json = values.get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存内容反序列化失败: " + key, e);
        }
    }

    /// 旁路缓存读取：命中直接返回；未命中则调用 `loader` 加载并回写缓存。
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            set(key, loaded, ttl);
        }
        return loaded;
    }

    /// 删除缓存。
    public void evict(String key) {
        keys.del(key);
    }

    private <T> String toJson(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存内容序列化失败", e);
        }
    }
}
