package io.github.foundry4j.quarkforge.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;

/// ## TokenRevocationAugmentor
/// 在认证阶段统一校验令牌是否已被撤销：命中黑名单时抛
/// [AuthenticationFailedException]，请求直接返回 401，无需业务代码配合。
///
/// 覆盖 [TokenBlacklist] 的两种维度：
///
/// - 当前令牌的 `jti` 被登出吊销；
/// - 当前用户在此前发生过改密/封号，`iat` 早于撤销时间点。
@ApplicationScoped
public class TokenRevocationAugmentor implements SecurityIdentityAugmentor {

    private final TokenBlacklist blacklist;

    @Inject
    public TokenRevocationAugmentor(TokenBlacklist blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        if (revoked(jwt)) {
            return Uni.createFrom().failure(new AuthenticationFailedException("令牌已被撤销"));
        }
        return Uni.createFrom().item(identity);
    }

    private boolean revoked(JsonWebToken jwt) {
        String jti = jwt.getTokenID();
        if (jti != null && blacklist.isRevoked(jti)) {
            return true;
        }
        String subject = jwt.getSubject();
        long issuedAt = jwt.getIssuedAtTime();
        return subject != null && issuedAt > 0
                && blacklist.isUserRevoked(subject, Instant.ofEpochSecond(issuedAt));
    }
}
