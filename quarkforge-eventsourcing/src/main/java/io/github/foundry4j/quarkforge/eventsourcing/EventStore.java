package io.github.foundry4j.quarkforge.eventsourcing;

import java.util.List;

/// ## EventStore
/// 事件存储 SPI。默认实现为 [JpaEventStore]（关系库 + JSON 负载），可自行替换为
/// Kafka、EventStoreDB 等实现。
public interface EventStore {

    /// 聚合当前已落库的事件数。
    long versionOf(String aggregateType, String aggregateId);

    /// 按序号升序加载聚合的全部事件。
    List<DomainEvent> loadEvents(String aggregateType, String aggregateId);

    /// 追加事件。
    ///
    /// @param aggregateType   聚合类型
    /// @param aggregateId     聚合 ID
    /// @param expectedVersion 期望的已落库事件数；不一致说明有并发写入，抛
    ///                        [OptimisticConcurrencyException]
    /// @param events          新事件，序号由存储层按期望版本依次分配
    void append(String aggregateType, String aggregateId, long expectedVersion, List<DomainEvent> events);

    /// 按主键顺序读取事件流（投影追赶用），`afterEventId` 传 0 表示从头开始。
    List<StoredEvent> readAll(long afterEventId, int limit);
}
