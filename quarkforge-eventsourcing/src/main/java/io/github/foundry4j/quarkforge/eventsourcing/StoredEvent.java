package io.github.foundry4j.quarkforge.eventsourcing;

import java.time.Instant;

/// ## StoredEvent
/// 已持久化的领域事件视图，供投影/审计/调试读取。
///
/// @param id            事件主键（雪花 ID，单调递增，可作为投影位点）
/// @param aggregateType 聚合类型
/// @param aggregateId   聚合 ID
/// @param sequenceNo    聚合内序号，从 1 开始
/// @param eventType     事件类全名
/// @param payload       反序列化后的事件对象
/// @param occurredAt    事件落库时间
public record StoredEvent(Long id,
                          String aggregateType,
                          String aggregateId,
                          long sequenceNo,
                          String eventType,
                          DomainEvent payload,
                          Instant occurredAt) {
}
