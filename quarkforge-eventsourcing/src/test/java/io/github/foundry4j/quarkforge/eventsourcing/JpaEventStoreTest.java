package io.github.foundry4j.quarkforge.eventsourcing;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## JpaEventStoreTest
/// 事件追加、序号分配、版本冲突与聚合仓储的读写往返。
@QuarkusTest
class JpaEventStoreTest {

    @Inject
    EventStore store;

    @Inject
    TestAccountRepository accounts;

    @Inject
    StoredEventRepository storedEvents;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> storedEvents.deleteAll());
    }

    @Test
    void appendAssignsSequenceAndLoadsInOrder() {
        store.append("TestAccount", "A1", 0, List.of(
                new AccountOpened("A001", new BigDecimal("10")),
                new MoneyDeposited(new BigDecimal("5"))));

        assertEquals(2, store.versionOf("TestAccount", "A1"));
        List<DomainEvent> events = store.loadEvents("TestAccount", "A1");
        assertEquals(List.of(AccountOpened.class, MoneyDeposited.class),
                events.stream().map(Object::getClass).toList());

        List<StoredEvent> stored = store.readAll(0, 10);
        assertEquals(2, stored.size());
        assertEquals(List.of(1L, 2L), stored.stream().map(StoredEvent::sequenceNo).toList());
        assertEquals("TestAccount", stored.getFirst().aggregateType());
    }

    @Test
    void rejectsStaleExpectedVersion() {
        store.append("TestAccount", "A2", 0, List.of(new AccountOpened("A002", new BigDecimal("10"))));

        OptimisticConcurrencyException error = assertThrows(OptimisticConcurrencyException.class,
                () -> store.append("TestAccount", "A2", 0, List.of(new MoneyDeposited(BigDecimal.ONE))));

        assertTrue(error.getMessage().contains("期望版本 0"));
        assertEquals(1, store.versionOf("TestAccount", "A2"));
    }

    @Test
    void repositoryRoundTrip() {
        TestAccount account = TestAccount.open("A003", new BigDecimal("100"));
        accounts.save("A003", account);

        assertTrue(account.uncommittedEvents().isEmpty());
        assertEquals(1, accounts.versionOf("A003"));

        TestAccount loaded = accounts.load("A003");
        assertEquals("A003", loaded.accountNo());
        assertEquals("100", loaded.balance().toPlainString());

        loaded.deposit(new BigDecimal("25"));
        accounts.save("A003", loaded);

        TestAccount reloaded = accounts.load("A003");
        assertEquals("125", reloaded.balance().toPlainString());
        assertEquals(2, reloaded.version());
        assertEquals(2, accounts.versionOf("A003"));
    }

    @Test
    void loadMissingAggregateReturnsEmpty() {
        TestAccount missing = accounts.load("not-exists");

        assertEquals(0, missing.version());
        assertTrue(missing.uncommittedEvents().isEmpty());
    }
}
