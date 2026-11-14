package io.github.foundry4j.quarkforge.redis;

import io.github.foundry4j.quarkforge.security.TokenBlacklist;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;

/// ## RedisTokenBlacklist
/// [TokenBlacklist] 的 Redis 分布式实现，多实例共享登出/改密/封号的撤销状态，
/// 重启后仍然生效。
///
/// 键设计：
///
/// - `quarkforge:blacklist:jti:<jti>`：值为 `1`，TTL 到令牌自身过期时间；
/// - `quarkforge:blacklist:user:<userId>`：值为撤销时间点（epoch 秒），
///   TTL 取访问令牌有效期 + 1 小时，保证比任何存量令牌存活更久。
@ApplicationScoped
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final String JTI_PREFIX = "quarkforge:blacklist:jti:";
    private static final String USER_PREFIX = "quarkforge:blacklist:user:";

    private final ValueCommands<String, String> values;
    private final long userRetentionSeconds;

    @Inject
    public RedisTokenBlacklist(RedisDataSource dataSource,
                               @ConfigProperty(name = "quarkforge.jwt.ttl-minutes", defaultValue = "120") long ttlMinutes) {
        this.values = dataSource.value(String.class);
        this.userRetentionSeconds = (ttlMinutes + 60) * 60;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        long ttl = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttl <= 0) {
            return;
        }
        values.setex(JTI_PREFIX + jti, ttl, "1");
    }

    @Override
    public boolean isRevoked(String jti) {
        return jti != null && values.get(JTI_PREFIX + jti) != null;
    }

    @Override
    public void revokeIssuedBefore(String userId, Instant revokedBefore) {
        if (userId == null || revokedBefore == null) {
            return;
        }
        values.setex(USER_PREFIX + userId, userRetentionSeconds, String.valueOf(revokedBefore.getEpochSecond()));
    }

    @Override
    public boolean isUserRevoked(String userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        String cutoff = values.get(USER_PREFIX + userId);
        return cutoff != null && issuedAt.getEpochSecond() <= Long.parseLong(cutoff);
    }
}
