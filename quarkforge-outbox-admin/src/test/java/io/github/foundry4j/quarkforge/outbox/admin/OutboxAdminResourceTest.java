package io.github.foundry4j.quarkforge.outbox.admin;

import io.github.foundry4j.quarkforge.outbox.OutboxMessage;
import io.github.foundry4j.quarkforge.outbox.OutboxRepository;
import io.github.foundry4j.quarkforge.outbox.OutboxStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/// ## OutboxAdminResourceTest
/// 管理端点的鉴权、查询与重放行为。
@QuarkusTest
class OutboxAdminResourceTest {

    @Inject
    OutboxRepository repository;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(repository::deleteAll);
    }

    @Test
    void requiresAdminRole() {
        given().when().get("/outbox/admin/messages")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "root", roles = "admin")
    void listsFailedMessages() {
        seed("order.created", OutboxStatus.FAILED, 3, "timeout");

        given().when().get("/outbox/admin/messages")
                .then().statusCode(200)
                .body("data.total", equalTo(1))
                .body("data.records", hasSize(1))
                .body("data.records[0].status", equalTo("FAILED"))
                .body("data.records[0].destination", equalTo("order.created"))
                .body("data.records[0].lastError", equalTo("timeout"));
    }

    @Test
    @TestSecurity(user = "root", roles = "admin")
    void replaysSingleMessage() {
        OutboxMessage message = seed("order.created", OutboxStatus.FAILED, 4, "boom");

        given().when().post("/outbox/admin/messages/{id}/replay", message.id)
                .then().statusCode(200)
                .body("code", equalTo(200));

        OutboxMessage replayed = repository.findById(message.id);
        org.junit.jupiter.api.Assertions.assertEquals(OutboxStatus.PENDING, replayed.status);
    }

    @Test
    @TestSecurity(user = "root", roles = "admin")
    void replaysBatchByCondition() {
        seed("order.created", OutboxStatus.FAILED, 2, "e1");
        seed("order.created", OutboxStatus.FAILED, 2, "e2");
        seed("stock.changed", OutboxStatus.FAILED, 2, "e3");

        given().contentType("application/json")
                .body("{\"destination\":\"order.created\",\"limit\":10}")
                .when().post("/outbox/admin/messages/replay")
                .then().statusCode(200)
                .body("data", equalTo(2));
    }

    private OutboxMessage seed(String destination, OutboxStatus status, int attempts, String lastError) {
        return QuarkusTransaction.requiringNew().call(() -> {
            OutboxMessage message = new OutboxMessage();
            message.messageId = UUID.randomUUID().toString();
            message.destination = destination;
            message.payload = "{}";
            message.status = status;
            message.attempts = attempts;
            message.nextAttemptAt = Instant.now();
            message.lastError = lastError;
            repository.persist(message);
            return message;
        });
    }
}
