package io.github.foundry4j.quarkforge.auth.entity;

import io.github.foundry4j.quarkforge.data.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/// ## RefreshToken
/// 刷新令牌持久化实体。出于安全考虑，数据库只保存令牌的 SHA-256 哈希，
/// 明文令牌只在签发响应中返回给客户端一次。
///
/// 刷新采用轮换策略：每次刷新都会吊销旧令牌并签发新令牌；
/// 已吊销令牌被再次使用视为泄露，会吊销该用户的全部令牌。
@Entity
@Table(name = "qf_refresh_token", indexes = {
        @Index(name = "uk_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_token_user", columnList = "user_id"),
        @Index(name = "idx_refresh_token_expires", columnList = "expires_at")})
public class RefreshToken extends BaseEntity {

    /// 明文令牌的 SHA-256 十六进制摘要。
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    public String tokenHash;

    /// 所属用户 ID，对应 JWT `sub` claim。
    @Column(name = "user_id", nullable = false, length = 64)
    public String userId;

    /// 过期时间。
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    /// 吊销时间，`null` 表示仍然有效。
    @Column(name = "revoked_at")
    public Instant revokedAt;

    /// 在指定时刻是否仍可用于刷新。
    public boolean usable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
