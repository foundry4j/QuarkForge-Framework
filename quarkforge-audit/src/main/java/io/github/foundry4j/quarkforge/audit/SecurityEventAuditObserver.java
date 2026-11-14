package io.github.foundry4j.quarkforge.audit;

import io.github.foundry4j.quarkforge.security.SecurityAuditEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/// ## SecurityEventAuditObserver
/// 监听 [SecurityAuditEvent] 并写入审计日志。
///
/// 审计写入异常只记录告警，不影响登录、鉴权等主流程。
@ApplicationScoped
public class SecurityEventAuditObserver {

    private static final Logger LOG = Logger.getLogger(SecurityEventAuditObserver.class);

    @Inject
    SecurityAuditWriter writer;

    @Inject
    @ConfigProperty(name = "quarkforge.audit.enabled", defaultValue = "true")
    boolean enabled;

    void onSecurityEvent(@Observes SecurityAuditEvent event) {
        if (!enabled) {
            return;
        }
        try {
            writer.write(event);
        } catch (Exception e) {
            LOG.warnf(e, "安全审计写入失败 action=%s actor=%s", event.action(), event.actor());
        }
    }
}
