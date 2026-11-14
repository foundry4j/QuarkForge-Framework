package io.github.foundry4j.quarkforge.outbox.admin;

import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.outbox.OutboxMessage;
import io.github.foundry4j.quarkforge.outbox.OutboxRepository;
import io.github.foundry4j.quarkforge.outbox.OutboxStatus;
import io.github.foundry4j.quarkforge.web.api.PageQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## OutboxAdminServiceTest
/// 失败消息查询、单条重放与按条件批量重放。
@QuarkusTest
class OutboxAdminServiceTest {

    @Inject
    OutboxAdminService service;

    @Inject
    OutboxRepository repository;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(repository::deleteAll);
    }

    @Test
    void listsFailedMessagesByDefault() {
        seed("order.created", OutboxStatus.FAILED, 3, "connect timeout");
        seed("order.created", OutboxStatus.SENT, 1, null);

        PageResult<OutboxMessageView> page = service.list(null, null, new PageQuery());

        assertEquals(1, page.total());
        assertEquals(1, page.records().size());
        assertEquals("FAILED", page.records().getFirst().status());
        assertEquals("connect timeout", page.records().getFirst().lastError());
    }

    @Test
    void filtersByDestination() {
        seed("order.created", OutboxStatus.FAILED, 1, "e1");
        seed("stock.changed", OutboxStatus.FAILED, 1, "e2");

        PageResult<OutboxMessageView> page = service.list(OutboxStatus.FAILED, "stock.changed", new PageQuery());

        assertEquals(1, page.total());
        assertEquals("stock.changed", page.records().getFirst().destination());
    }

    @Test
    void replaysFailedMessage() {
        OutboxMessage message = seed("order.created", OutboxStatus.FAILED, 5, "boom");

        service.replay(message.id);

        OutboxMessage replayed = repository.findById(message.id);
        assertEquals(OutboxStatus.PENDING, replayed.status);
        assertEquals(0, replayed.attempts);
        assertNull(replayed.lastError);
    }

    @Test
    void rejectsReplayOfNonFailedMessage() {
        OutboxMessage message = seed("order.created", OutboxStatus.SENT, 1, null);

        BizException error = assertThrows(BizException.class, () -> service.replay(message.id));

        assertEquals(40900, error.errorCode().code());
    }

    @Test
    void rejectsReplayOfMissingMessage() {
        BizException error = assertThrows(BizException.class, () -> service.replay(-1L));

        assertEquals(40400, error.errorCode().code());
    }

    @Test
    void batchReplayRespectsDestinationAndLimit() {
        seed("order.created", OutboxStatus.FAILED, 1, "e1");
        seed("order.created", OutboxStatus.FAILED, 1, "e2");
        seed("order.created", OutboxStatus.FAILED, 1, "e3");
        seed("stock.changed", OutboxStatus.FAILED, 1, "e4");
        seed("order.created", OutboxStatus.SENT, 1, null);

        int replayed = service.replayBatch(new OutboxReplayRequest("order.created", null, 1));

        assertEquals(1, replayed);
        assertEquals(2, service.list(OutboxStatus.FAILED, "order.created", new PageQuery()).total());
        assertEquals(1, service.list(OutboxStatus.FAILED, "stock.changed", new PageQuery()).total());
    }

    @Test
    void batchReplayHonoursBeforeFilter() {
        OutboxMessage old = seed("order.created", OutboxStatus.FAILED, 1, "e1");
        OutboxMessage young = seed("order.created", OutboxStatus.FAILED, 1, "e2");

        int replayed = service.replayBatch(new OutboxReplayRequest(null, Instant.now().plusSeconds(1), 10));

        assertEquals(2, replayed);
        assertEquals(OutboxStatus.PENDING, repository.findById(old.id).status);
        assertEquals(OutboxStatus.PENDING, repository.findById(young.id).status);
        assertTrue(service.list(OutboxStatus.FAILED, null, new PageQuery()).records().isEmpty());
    }

    private OutboxMessage seed(String destination, OutboxStatus status, int attempts, String lastError) {
        return QuarkusTransaction.requiringNew().call(() -> {
            OutboxMessage message = new OutboxMessage();
            message.messageId = UUID.randomUUID().toString();
            message.destination = destination;
            message.payload = "{\"id\":1}";
            message.status = status;
            message.attempts = attempts;
            message.nextAttemptAt = Instant.now();
            message.lastError = lastError;
            repository.persist(message);
            return message;
        });
    }
}
