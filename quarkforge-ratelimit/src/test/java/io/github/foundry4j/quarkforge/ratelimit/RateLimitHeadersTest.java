package io.github.foundry4j.quarkforge.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// ## RateLimitHeadersTest
/// 限流响应头的名称与取值。
class RateLimitHeadersTest {

    private final Map<String, String> headers = new LinkedHashMap<>();

    @Test
    void writesStandardHeadersWhenAllowed() {
        RateLimitHeaders.apply(new RateLimitResult(true, 10, 7, 1_760_000_000L, 0), headers::put);

        assertEquals("10", headers.get("X-RateLimit-Limit"));
        assertEquals("7", headers.get("X-RateLimit-Remaining"));
        assertEquals("1760000000", headers.get("X-RateLimit-Reset"));
        assertFalse(headers.containsKey("Retry-After"));
    }

    @Test
    void writesRetryAfterWhenRejected() {
        RateLimitHeaders.apply(new RateLimitResult(false, 10, 0, 1_760_000_000L, 5), headers::put);

        assertEquals("0", headers.get("X-RateLimit-Remaining"));
        assertEquals("5", headers.get("Retry-After"));
    }

    @Test
    void ignoresMissingResponse() {
        RateLimitHeaders.apply(null, new RateLimitResult(true, 1, 0, 0, 0));
    }
}
