package io.github.foundry4j.quarkforge.redis;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// ## RedisTokenBlacklistTest
/// 用 Mockito 替身验证 Redis 键设计、TTL 与用户级撤销的比较逻辑。
@SuppressWarnings("unchecked")
class RedisTokenBlacklistTest {

    private final RedisDataSource dataSource = mock(RedisDataSource.class);
    private final ValueCommands<String, String> values = mock(ValueCommands.class);
    private final RedisTokenBlacklist blacklist;

    RedisTokenBlacklistTest() {
        when(dataSource.value(String.class)).thenReturn(values);
        blacklist = new RedisTokenBlacklist(dataSource, 120);
    }

    @Test
    void revokeWritesJtiWithTokenRemainingTtl() {
        blacklist.revoke("j1", Instant.now().plusSeconds(90));

        verify(values).setex(eq("quarkforge:blacklist:jti:j1"),
                longThat(ttl -> ttl > 0 && ttl <= 90), eq("1"));
    }

    @Test
    void skipsAlreadyExpiredToken() {
        blacklist.revoke("j1", Instant.now().minusSeconds(1));

        verify(values, org.mockito.Mockito.never()).setex(
                eq("quarkforge:blacklist:jti:j1"), longThat(_ -> true), eq("1"));
    }

    @Test
    void readsRevokedFlagFromRedis() {
        when(values.get("quarkforge:blacklist:jti:j1")).thenReturn("1");

        assertTrue(blacklist.isRevoked("j1"));
        assertFalse(blacklist.isRevoked("j2"));
        assertFalse(blacklist.isRevoked(null));
    }

    @Test
    void comparesIssuedAtWithUserCutoff() {
        Instant cutoff = Instant.ofEpochSecond(1_700_000_000L);
        when(values.get("quarkforge:blacklist:user:u1")).thenReturn("1700000000");

        assertTrue(blacklist.isUserRevoked("u1", cutoff));
        assertTrue(blacklist.isUserRevoked("u1", cutoff.minusSeconds(30)));
        assertFalse(blacklist.isUserRevoked("u1", cutoff.plusSeconds(1)));
        assertFalse(blacklist.isUserRevoked("u2", cutoff));
    }

    @Test
    void userCutoffOutlivesAccessTokenTtl() {
        Instant cutoff = Instant.ofEpochSecond(1_700_000_000L);

        blacklist.revokeIssuedBefore("u1", cutoff);

        // ttl-minutes=120，保留 120 + 60 分钟
        verify(values).setex("quarkforge:blacklist:user:u1", 10_800L, "1700000000");
    }
}
