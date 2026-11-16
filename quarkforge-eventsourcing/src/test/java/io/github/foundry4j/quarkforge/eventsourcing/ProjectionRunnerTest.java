package io.github.foundry4j.quarkforge.eventsourcing;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## ProjectionRunnerTest
/// 投影按批次追赶事件流，位点持久化后不重复处理。
@QuarkusTest
class ProjectionRunnerTest {

    @Inject
    EventStore store;

    @Inject
    ProjectionRunner runner;

    @Inject
    StoredEventRepository storedEvents;

    @Inject
    ProjectionCheckpointRepository checkpoints;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> {
            storedEvents.deleteAll();
            checkpoints.deleteAll();
        });
    }

    @Test
    void appliesEachEventOnceAcrossBatches() {
        store.append("TestAccount", "B1", 0, List.of(
                new AccountOpened("B001", new BigDecimal("10")),
                new MoneyDeposited(new BigDecimal("5"))));
        store.append("TestAccount", "B2", 0, List.of(new AccountOpened("B002", new BigDecimal("20"))));

        List<String> handled = new ArrayList<>();
        EventProjection projection = new EventProjection() {
            @Override
            public String name() {
                return "test-projection";
            }

            @Override
            public void handle(StoredEvent event) {
                handled.add(event.aggregateId());
            }
        };

        assertEquals(2, runner.catchUp(projection, 2));
        assertEquals(1, runner.catchUp(projection, 10));
        assertEquals(0, runner.catchUp(projection, 10));

        assertEquals(List.of("B1", "B1", "B2"), handled);
        assertTrue(runner.checkpointOf("test-projection") > 0);
    }

    @Test
    void checkpointStartsAtZero() {
        assertEquals(0, runner.checkpointOf("never-run"));
    }
}
