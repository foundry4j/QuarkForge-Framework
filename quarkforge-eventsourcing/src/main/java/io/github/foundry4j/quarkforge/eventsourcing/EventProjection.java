package io.github.foundry4j.quarkforge.eventsourcing;

/// ## EventProjection
/// CQRS 读模型投影：消费事件流并更新查询侧数据（如报表表、搜索索引）。
///
/// 实现应为幂等或依赖 [ProjectionRunner] 的位点保证「每条事件只处理一次」：
/// 投影位点与业务写入在同一事务内提交，失败时一起回滚。
public interface EventProjection {

    /// 投影名称，作为位点主键，需全局唯一且稳定。
    String name();

    /// 处理单条事件。
    void handle(StoredEvent event);
}
