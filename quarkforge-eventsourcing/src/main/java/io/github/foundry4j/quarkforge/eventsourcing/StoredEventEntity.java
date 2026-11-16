package io.github.foundry4j.quarkforge.eventsourcing;

import io.github.foundry4j.quarkforge.data.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/// ## StoredEventEntity
/// 事件表：一行一个领域事件，`(聚合类型, 聚合 ID, 序号)` 唯一，保证同一聚合的事件有序且不重复。
@Entity
@Table(name = "qf_stored_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_stored_event_aggregate",
                columnNames = {"aggregate_type", "aggregate_id", "sequence_no"}),
        indexes = @Index(name = "idx_stored_event_aggregate", columnList = "aggregate_type,aggregate_id"))
public class StoredEventEntity extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 128)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    public String aggregateId;

    @Column(name = "sequence_no", nullable = false)
    public long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 255)
    public String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    public String payload;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
