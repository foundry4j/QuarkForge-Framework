package io.github.foundry4j.quarkforge.eventsourcing;

import java.util.ArrayList;
import java.util.List;

/// ## AggregateRoot
/// 事件溯源聚合根基类：状态只能通过事件变更，当前状态是全部历史事件重放的结果。
///
/// ```java
/// public class Order extends AggregateRoot {
///     private String status;
///
///     public static Order create(String orderNo) {
///         Order order = new Order();
///         order.apply(new OrderCreated(orderNo));
///         return order;
///     }
///
///     public void pay() {
///         apply(new OrderPaid());
///     }
///
///     @Override
///     protected void mutate(DomainEvent event) {
///         switch (event) {
///             case OrderCreated e -> status = "CREATED";
///             case OrderPaid e -> status = "PAID";
///             default -> throw new IllegalStateException("未知事件: " + event);
///         }
///     }
/// }
/// ```
///
/// - [apply] 立即改动内存状态，并把事件记入未提交列表，等待仓储持久化
/// - [replay] 只重放历史事件，不产生未提交事件
/// - [expectedVersion] 用于持久化时的乐观并发校验（已落库的事件数）
public abstract class AggregateRoot {

    private long version;

    private final List<DomainEvent> uncommitted = new ArrayList<>();

    /// 当前版本号，等于已应用事件总数。
    public long version() {
        return version;
    }

    /// 应用一个新事件：变更状态、累加版本并记录待提交事件。
    protected void apply(DomainEvent event) {
        mutate(event);
        uncommitted.add(event);
        version++;
    }

    /// 根据事件推进内存状态，由聚合根实现（只做状态变更，不要产生副作用）。
    protected abstract void mutate(DomainEvent event);

    /// 未提交事件快照。
    public List<DomainEvent> uncommittedEvents() {
        return List.copyOf(uncommitted);
    }

    /// 持久化成功后清空未提交事件。
    public void markEventsCommitted() {
        uncommitted.clear();
    }

    /// 重放历史事件（从事件存储加载后调用），不产生未提交事件。
    public void replay(List<? extends DomainEvent> history) {
        for (DomainEvent event : history) {
            mutate(event);
            version++;
        }
    }

    /// 已落库的事件数，作为下次追加时的期望版本。
    public long expectedVersion() {
        return version - uncommitted.size();
    }
}
