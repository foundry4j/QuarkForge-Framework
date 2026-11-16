package io.github.foundry4j.quarkforge.eventsourcing;

import io.github.foundry4j.quarkforge.data.BaseRepository;
import jakarta.enterprise.context.ApplicationScoped;

/// 投影位点仓储。
@ApplicationScoped
public class ProjectionCheckpointRepository extends BaseRepository<ProjectionCheckpoint> {
}
