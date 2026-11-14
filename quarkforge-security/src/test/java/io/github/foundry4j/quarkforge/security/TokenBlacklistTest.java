package io.github.foundry4j.quarkforge.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## TokenBlacklistTest
/// 内存黑名单的 jti 撤销、过期清理与用户级批量撤销语义。
class TokenBlacklistTest {

    private final InMemoryTokenBlacklist blacklist = new InMemoryTokenBlacklist();

    @Test
    void revokesTokenUntilItsExpiry() {
        blacklist.revoke("j1", Instant.now().plusSeconds(60));

        assertTrue(blacklist.isRevoked("j1"));
    }

    @Test
    void ignoresExpiredOrUnknownTokens() {
        blacklist.revoke("expired", Instant.now().minusSeconds(1));

        assertFalse(blacklist.isRevoked("expired"));
        assertFalse(blacklist.isRevoked("unknown"));
        assertFalse(blacklist.isRevoked(null));
    }

    @Test
    void revokesTokensIssuedBeforeCutoff() {
        Instant cutoff = Instant.now();

        blacklist.revokeIssuedBefore("u1", cutoff);

        assertTrue(blacklist.isUserRevoked("u1", cutoff.minusSeconds(10)));
        assertTrue(blacklist.isUserRevoked("u1", cutoff));
        assertFalse(blacklist.isUserRevoked("u1", cutoff.plusSeconds(1)));
        assertFalse(blacklist.isUserRevoked("u2", cutoff));
    }

    @Test
    void keepsLatestCutoffOnRepeatedRevocation() {
        Instant earlier = Instant.now().minusSeconds(60);
        Instant later = Instant.now();

        blacklist.revokeIssuedBefore("u1", later);
        blacklist.revokeIssuedBefore("u1", earlier);

        assertTrue(blacklist.isUserRevoked("u1", earlier.minusSeconds(1)));
        assertFalse(blacklist.isUserRevoked("u1", later.plusSeconds(1)));
    }
}
