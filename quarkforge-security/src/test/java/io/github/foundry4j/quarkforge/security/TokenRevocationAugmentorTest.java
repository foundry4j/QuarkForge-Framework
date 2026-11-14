package io.github.foundry4j.quarkforge.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// ## TokenRevocationAugmentorTest
/// 认证阶段对已撤销令牌的拦截行为。
class TokenRevocationAugmentorTest {

    private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
    private final TokenRevocationAugmentor augmentor = new TokenRevocationAugmentor(blacklist);
    private final AuthenticationRequestContext context = mock(AuthenticationRequestContext.class);

    @Test
    void rejectsRevokedJti() throws Exception {
        Instant issuedAt = Instant.now();
        SecurityIdentity identity = identity("u1", "j1", issuedAt);
        when(blacklist.isRevoked("j1")).thenReturn(true);

        assertRejected(augmentor.augment(identity, context));
    }

    @Test
    void rejectsTokensIssuedBeforeUserRevocation() throws Exception {
        // JWT 的 iat 精度到秒，stub 与实际比较值保持一致
        Instant issuedAt = Instant.ofEpochSecond(Instant.now().getEpochSecond());
        SecurityIdentity identity = identity("u1", "j2", issuedAt);
        when(blacklist.isUserRevoked("u1", issuedAt)).thenReturn(true);

        assertRejected(augmentor.augment(identity, context));
    }

    @Test
    void keepsValidIdentity() {
        Instant issuedAt = Instant.now();
        SecurityIdentity identity = identity("u1", "j3", issuedAt);

        SecurityIdentity result = augmentor.augment(identity, context).await().indefinitely();

        assertSame(identity, result);
    }

    private static SecurityIdentity identity(String userId, String jti, Instant issuedAt) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(userId);
        when(jwt.getTokenID()).thenReturn(jti);
        when(jwt.getIssuedAtTime()).thenReturn(issuedAt.getEpochSecond());
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        return identity;
    }

    private static void assertRejected(Uni<SecurityIdentity> augmented) throws Exception {
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> augmented.subscribeAsCompletionStage().get(5, TimeUnit.SECONDS));
        assertInstanceOf(AuthenticationFailedException.class, error.getCause());
    }
}
