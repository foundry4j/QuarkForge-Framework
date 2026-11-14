package io.github.foundry4j.quarkforge.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.foundry4j.quarkforge.security.SecurityAuditEvent;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;

/// ## SecurityAuditWriter
/// 把 [SecurityAuditEvent] 落库为审计日志：
///
/// - `entityType` 固定为 `SECURITY`
/// - `action` 为事件类型（`LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGOUT` / `TOKEN_REVOKED` / `ACCESS_DENIED`）
/// - `actor` 为操作人，`entityId` 为操作对象，`changes` 记录补充说明
///
/// 使用 `REQUIRES_NEW` 独立事务：登录失败、403 等场景业务事务会回滚，
/// 安全审计必须独立提交，否则会随业务异常一起丢失。
@ApplicationScoped
public class SecurityAuditWriter {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ConfigProperty(name = "quarkforge.audit.enabled", defaultValue = "true")
    boolean enabled;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void write(SecurityAuditEvent event) {
        if (!enabled || event == null) {
            return;
        }
        AuditLog log = new AuditLog();
        log.entityType = "SECURITY";
        log.entityId = truncate(event.target(), 64);
        log.action = truncate(event.action(), 16);
        log.actor = event.actor() == null || event.actor().isBlank()
                ? "anonymous" : truncate(event.actor(), 64);
        log.changes = detail(event.detail());
        log.occurredAt = Instant.now();
        Panache.getEntityManager().persist(log);
    }

    private String detail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("detail", detail));
        } catch (JsonProcessingException e) {
            return detail;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
