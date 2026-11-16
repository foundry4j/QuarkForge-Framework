package io.github.foundry4j.quarkforge.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## TtlJitterTest
/// TTL 抖动只向上浮动、比例有界，关闭时保持原值。
class TtlJitterTest {

    private static final Duration BASE = Duration.ofMinutes(5);

    @Test
    void keepsTtlWhenJitterDisabled() {
        assertEquals(BASE, TtlJitter.apply(BASE, 0));
        assertEquals(BASE, TtlJitter.apply(BASE, -0.2));
    }

    @Test
    void jittersUpwardWithinConfiguredRatio() {
        long baseNanos = BASE.toNanos();
        long maxNanos = (long) (baseNanos * 1.2);
        Set<Long> observed = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            long nanos = TtlJitter.apply(BASE, 0.2).toNanos();
            assertTrue(nanos >= baseNanos, "抖动不应缩短 TTL: " + nanos);
            assertTrue(nanos <= maxNanos, "抖动不应超过 20%: " + nanos);
            observed.add(nanos);
        }

        assertTrue(observed.size() > 1, "多次计算应产生不同抖动值");
    }

    @Test
    void clampsRatioToOne() {
        long maxNanos = BASE.toNanos() * 2;
        for (int i = 0; i < 100; i++) {
            assertTrue(TtlJitter.apply(BASE, 5.0).toNanos() <= maxNanos);
        }
    }

    @Test
    void leavesNonPositiveTtlUntouched() {
        assertEquals(Duration.ZERO, TtlJitter.apply(Duration.ZERO, 0.2));
        assertEquals(Duration.ofSeconds(-1), TtlJitter.apply(Duration.ofSeconds(-1), 0.2));
    }
}
