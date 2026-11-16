package io.github.foundry4j.quarkforge.eventsourcing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/// ## ProjectionRunner
/// 驱动读模型追赶事件流：按位点批量读取事件 → 交给投影处理 → 更新位点。
///
/// 位点更新与投影写入在同一事务中，处理失败会整体回滚，下次从同一位置重试；
/// 业务方可用定时任务、管理端点或消息触发 `catchUp`：
///
/// ```java
/// @Scheduled(every = "5s")
/// void sync() {
///     while (projectionRunner.catchUp(orderSummaryProjection, 200) == 200) {
///         // 还有积压，继续追
///     }
/// }
/// ```
@ApplicationScoped
public class ProjectionRunner {

    @Inject
    EventStore eventStore;

    @Inject
    ProjectionCheckpointRepository checkpoints;

    /// 追赶一批事件，返回本批处理条数。
    ///
    /// @param projection 目标投影
    /// @param batchSize  单批上限
    /// @return 实际处理的事件数；小于 `batchSize` 表示已追平
    @Transactional
    public int catchUp(EventProjection projection, int batchSize) {
        ProjectionCheckpoint checkpoint = checkpoints
                .find("projection = ?1", projection.name())
                .firstResult();
        long lastEventId = checkpoint == null ? 0 : checkpoint.lastEventId;

        List<StoredEvent> events = eventStore.readAll(lastEventId, Math.max(1, batchSize));
        for (StoredEvent event : events) {
            projection.handle(event);
            lastEventId = event.id();
        }
        if (checkpoint == null) {
            checkpoint = new ProjectionCheckpoint();
            checkpoint.projection = projection.name();
            checkpoint.lastEventId = lastEventId;
            checkpoints.persist(checkpoint);
        } else {
            checkpoint.lastEventId = lastEventId;
        }
        return events.size();
    }

    /// 当前投影位点，未初始化时为 0。
    public long checkpointOf(String projectionName) {
        ProjectionCheckpoint checkpoint = checkpoints
                .find("projection = ?1", projectionName)
                .firstResult();
        return checkpoint == null ? 0 : checkpoint.lastEventId;
    }
}
