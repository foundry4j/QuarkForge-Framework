package io.github.foundry4j.quarkforge.eventsourcing;

/// ## DomainEvent
/// 领域事件标记接口。实现类应为不可变对象（推荐 `record`）并能被 Jackson 序列化，
/// 事件以 `类全名 + JSON` 的形式存入事件表，重放时按类名反序列化。
///
/// ```java
/// public record OrderCreated(String orderNo, BigDecimal amount) implements DomainEvent { }
/// ```
public interface DomainEvent {
}
