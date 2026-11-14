package io.github.foundry4j.quarkforge.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

/// ## RefreshTokenRepository
/// 刷新令牌的数据访问入口，按令牌哈希查询、按用户批量吊销。
@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshToken> {

    /// 按明文令牌的哈希查找。
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return find("tokenHash", tokenHash).singleResultOptional();
    }

    /// 吊销指定用户的全部有效令牌，返回受影响行数。
    public int revokeAll(String userId, Instant now) {
        return update("revokedAt = ?1 where userId = ?2 and revokedAt is null", now, userId);
    }
}
