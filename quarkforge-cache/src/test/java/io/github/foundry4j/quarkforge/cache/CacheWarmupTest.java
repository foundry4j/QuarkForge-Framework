package io.github.foundry4j.quarkforge.cache;

import io.github.foundry4j.quarkforge.cache.annotation.CacheWarmup;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CacheWarmup] 的端到端测试：启动时自动执行、按 order 排序、失败隔离与非法方法跳过。
@QuarkusTest
class CacheWarmupTest {

    @Inject
    LocalCacheService cache;

    @Test
    void warmsCacheAtStartup() {
        assertNotNull(cache.get("qf:cache:warmup:first", String.class, Duration.ofMinutes(10)));
        assertNotNull(cache.get("qf:cache:warmup:second", String.class, Duration.ofMinutes(10)));
        assertEquals("second", cache.get("qf:cache:warmup:second", String.class, Duration.ofMinutes(10)));
    }

    @Test
    void runsTasksInOrderAndContinuesAfterFailure() {
        List<String> executed = WarmupService.EVENTS.stream().distinct().toList();
        assertEquals(List.of("first", "broken", "second"), executed);
    }

    @Test
    void skipsDisabledAndParameterizedMethods() {
        assertFalse(WarmupService.EVENTS.contains("disabled"));
        assertFalse(WarmupService.EVENTS.contains("withArgs"));
    }

    @Unremovable
    @ApplicationScoped
    static class WarmupService {

        static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

        @Inject
        CacheService cache;

        @CacheWarmup(name = "first", order = 10)
        public void first() {
            EVENTS.add("first");
            cache.put("qf:cache:warmup:first", "first", Duration.ofMinutes(10));
        }

        @CacheWarmup(name = "broken", order = 20)
        public void broken() {
            EVENTS.add("broken");
            throw new IllegalStateException("模拟预热失败");
        }

        @CacheWarmup(name = "second", order = 30)
        public void second() {
            EVENTS.add("second");
            cache.put("qf:cache:warmup:second", "second", Duration.ofMinutes(10));
        }

        @CacheWarmup(order = 40, enabled = false)
        public void disabled() {
            EVENTS.add("disabled");
        }

        @CacheWarmup(order = 50)
        public void withArgs(String value) {
            EVENTS.add("withArgs");
        }
    }
}
