package io.github.foundry4j.quarkforge.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// 测试用安全审计事件收集器。
@ApplicationScoped
public class SecurityAuditCollector {

    private static final List<SecurityAuditEvent> EVENTS = new CopyOnWriteArrayList<>();

    void collect(@Observes SecurityAuditEvent event) {
        EVENTS.add(event);
    }

    public static List<SecurityAuditEvent> events() {
        return List.copyOf(EVENTS);
    }

    public static void clear() {
        EVENTS.clear();
    }
}
