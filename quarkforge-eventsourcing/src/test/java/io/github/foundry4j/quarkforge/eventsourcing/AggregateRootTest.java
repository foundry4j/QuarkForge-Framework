package io.github.foundry4j.quarkforge.eventsourcing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## AggregateRootTest
/// 聚合根的版本、待提交事件与重放行为。
class AggregateRootTest {

    @Test
    void appliesEventsAndTracksVersion() {
        TestAccount account = TestAccount.open("A001", new BigDecimal("100"));
        assertEquals(1, account.version());
        assertEquals(0, account.expectedVersion());
        assertEquals(1, account.uncommittedEvents().size());

        account.deposit(new BigDecimal("25"));

        assertEquals("125", account.balance().toPlainString());
        assertEquals(2, account.version());
        assertEquals(0, account.expectedVersion());
        assertEquals(2, account.uncommittedEvents().size());
    }

    @Test
    void replayRebuildsStateWithoutNewChanges() {
        TestAccount source = TestAccount.open("A001", new BigDecimal("100"));
        source.deposit(new BigDecimal("25"));
        List<DomainEvent> history = source.uncommittedEvents();

        TestAccount replayed = new TestAccount();
        replayed.replay(history);

        assertEquals("A001", replayed.accountNo());
        assertEquals("125", replayed.balance().toPlainString());
        assertEquals(2, replayed.version());
        assertEquals(2, replayed.expectedVersion());
        assertTrue(replayed.uncommittedEvents().isEmpty());
    }

    @Test
    void markCommittedClearsPendingEvents() {
        TestAccount account = TestAccount.open("A001", new BigDecimal("100"));
        account.markEventsCommitted();

        assertTrue(account.uncommittedEvents().isEmpty());
        assertEquals(1, account.version());
        assertEquals(1, account.expectedVersion());
    }
}
