package io.github.foundry4j.quarkforge.security;

import java.time.Instant;

/// ## TokenBlacklist
/// 令牌撤销 SPI。默认实现 [InMemoryTokenBlacklist]（单机内存）；
/// 引入 `quarkforge-redis` 后由 `RedisTokenBlacklist` 覆盖，多实例共享撤销状态。
///
/// 两种撤销维度：
///
/// - **按 jti**：登出时吊销当前访问令牌，记录保留到令牌自然过期；
/// - **按用户**：改密、封号时记录“该用户在某时刻之前签发的令牌全部失效”，
///   无需枚举用户已有的 jti。判定条件为 `issuedAt <= revokedBefore`。
///
/// 校验入口是 [TokenRevocationAugmentor]：被撤销的令牌会在认证阶段直接判为 401。
public interface TokenBlacklist {

    /// 吊销单个令牌，`expiresAt` 为令牌自身的过期时间，过期后记录可清理。
    void revoke(String jti, Instant expiresAt);

    /// 指定 jti 是否已被吊销。
    boolean isRevoked(String jti);

    /// 吊销指定用户在 `revokedBefore` 之前签发的全部令牌。
    void revokeIssuedBefore(String userId, Instant revokedBefore);

    /// 判断某用户某签发时间的令牌是否已被批量吊销。
    boolean isUserRevoked(String userId, Instant issuedAt);
}
