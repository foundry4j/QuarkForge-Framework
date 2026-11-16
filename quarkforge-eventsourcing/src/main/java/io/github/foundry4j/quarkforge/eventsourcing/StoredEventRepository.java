package io.github.foundry4j.quarkforge.eventsourcing;

import io.github.foundry4j.quarkforge.data.BaseRepository;
import jakarta.enterprise.context.ApplicationScoped;

/// 事件表仓储。
@ApplicationScoped
public class StoredEventRepository extends BaseRepository<StoredEventEntity> {
}
