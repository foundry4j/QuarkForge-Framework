package io.github.foundry4j.quarkforge.security;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// ## InMemoryTokenBlacklist
/// 单机默认实现：jti 记录随令牌过期自动失效，用户级撤销记录常驻内存。
///
/// 只适合单实例部署；多实例或需要重启后仍生效时引入 `quarkforge-redis`，
/// Redis 实现会自动覆盖本 Bean。
@DefaultBean
@ApplicationScoped
public class InMemoryTokenBlacklist implements TokenBlacklist {

    /// jti 清理阈值，避免过期记录长期占用内存。
    private static final int PURGE_THRESHOLD = 10_000;

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> userCutoffs = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return;
        }
        revokedTokens.put(jti, expiresAt);
        if (revokedTokens.size() > PURGE_THRESHOLD) {
            Instant now = Instant.now();
            revokedTokens.values().removeIf(expiry -> !expiry.isAfter(now));
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expiresAt = revokedTokens.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isAfter(Instant.now())) {
            return true;
        }
        revokedTokens.remove(jti, expiresAt);
        return false;
    }

    @Override
    public void revokeIssuedBefore(String userId, Instant revokedBefore) {
        if (userId == null || revokedBefore == null) {
            return;
        }
        // 多次撤销取较晚的时间点，避免旧值覆盖新值
        userCutoffs.merge(userId, revokedBefore,
                (existing, incoming) -> existing.isAfter(incoming) ? existing : incoming);
    }

    @Override
    public boolean isUserRevoked(String userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        Instant cutoff = userCutoffs.get(userId);
        return cutoff != null && !issuedAt.isAfter(cutoff);
    }

    /// 清理已过期的 jti 记录，测试与运维排查时可主动调用。
    public void clearExpired() {
        Instant now = Instant.now();
        revokedTokens.values().removeIf(expiry -> !expiry.isAfter(now));
    }
}
