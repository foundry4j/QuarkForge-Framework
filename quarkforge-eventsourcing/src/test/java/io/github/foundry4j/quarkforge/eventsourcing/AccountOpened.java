package io.github.foundry4j.quarkforge.eventsourcing;

import java.math.BigDecimal;

/// 测试用领域事件。
public record AccountOpened(String accountNo, BigDecimal amount) implements DomainEvent {
}
