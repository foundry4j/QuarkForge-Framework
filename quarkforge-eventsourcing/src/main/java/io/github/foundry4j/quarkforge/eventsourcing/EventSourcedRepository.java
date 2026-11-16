package io.github.foundry4j.quarkforge.eventsourcing;

import jakarta.inject.Inject;

import java.util.List;

/// ## EventSourcedRepository
/// 事件溯源聚合仓储：加载 = 重放历史事件，保存 = 追加未提交事件。
///
/// ```java
/// @ApplicationScoped
/// public class OrderRepository extends EventSourcedRepository<Order> {
///     @Override protected String aggregateType() { return "Order"; }
///     @Override protected Order newAggregate() { return new Order(); }
/// }
///
/// Order order = Order.create("A001");
/// orderRepository.save("A001", order);
///
/// Order loaded = orderRepository.load("A001");   // 不存在时返回空聚合，version() == 0
/// ```
///
/// 保存采用乐观并发：聚合加载后若被其他请求先行提交，追加会抛
/// [OptimisticConcurrencyException]，重新 `load` 后重放业务动作即可。
public abstract class EventSourcedRepository<T extends AggregateRoot> {

    @Inject
    EventStore eventStore;

    /// 聚合类型标识，需全局唯一且稳定（建议直接用类名）。
    protected abstract String aggregateType();

    /// 创建空聚合实例，供事件重放填充状态。
    protected abstract T newAggregate();

    /// 加载聚合；不存在时返回空聚合（`version() == 0`）。
    public T load(String aggregateId) {
        T aggregate = newAggregate();
        List<DomainEvent> history = eventStore.loadEvents(aggregateType(), aggregateId);
        aggregate.replay(history);
        return aggregate;
    }

    /// 持久化聚合的未提交事件。
    public void save(String aggregateId, T aggregate) {
        eventStore.append(aggregateType(), aggregateId, aggregate.expectedVersion(),
                aggregate.uncommittedEvents());
        aggregate.markEventsCommitted();
    }

    /// 当前聚合版本（已落库事件数）。
    public long versionOf(String aggregateId) {
        return eventStore.versionOf(aggregateType(), aggregateId);
    }
}
