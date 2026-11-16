package io.github.foundry4j.quarkforge.eventsourcing;

import io.github.foundry4j.quarkforge.data.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/// ## ProjectionCheckpoint
/// 投影消费位点：记录每个投影已处理到的最大事件 ID。
@Entity
@Table(name = "qf_projection_checkpoint",
        uniqueConstraints = @UniqueConstraint(name = "uk_projection_checkpoint", columnNames = "projection"))
public class ProjectionCheckpoint extends BaseEntity {

    @Column(name = "projection", nullable = false, length = 128)
    public String projection;

    @Column(name = "last_event_id", nullable = false)
    public long lastEventId;
}
