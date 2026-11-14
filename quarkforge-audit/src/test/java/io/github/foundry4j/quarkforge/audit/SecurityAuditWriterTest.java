package io.github.foundry4j.quarkforge.audit;

import io.github.foundry4j.quarkforge.security.SecurityAuditEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## SecurityAuditWriterTest
/// 验证安全事件落库，以及业务事务回滚时审计仍能独立提交。
@QuarkusTest
class SecurityAuditWriterTest {

    @Inject
    SecurityAuditWriter writer;

    @Inject
    FailingService failing;

    @BeforeEach
    @Transactional
    void clean() {
        AuditLog.deleteAll();
    }

    @Test
    void writesSecurityEvent() {
        writer.write(SecurityAuditEvent.loginSuccess("1", "alice"));

        AuditLog log = AuditLog.<AuditLog>find("action", "LOGIN_SUCCESS").singleResult();
        assertEquals("SECURITY", log.entityType);
        assertEquals("1", log.entityId);
        assertEquals("alice", log.actor);
    }

    @Test
    void keepsAuditWhenBusinessTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> failing.fireAndFail());

        assertEquals(1L, AuditLog.count("action", "LOGIN_FAILURE"));
        AuditLog log = AuditLog.<AuditLog>find("action", "LOGIN_FAILURE").singleResult();
        assertEquals("ghost", log.actor);
        assertTrue(log.changes.contains("bad password"));
    }

    /// 模拟业务事务中发布事件后抛错回滚的登录失败场景。
    @ApplicationScoped
    public static class FailingService {

        @Inject
        Event<SecurityAuditEvent> events;

        @Transactional
        public void fireAndFail() {
            events.fire(SecurityAuditEvent.loginFailure("ghost", "bad password"));
            throw new IllegalStateException("boom");
        }
    }
}
