package io.github.foundry4j.quarkforge.excel;

import java.util.Optional;

/// 异步导出任务存储 SPI。默认实现是进程内的
/// [InMemoryExcelExportTaskStore]，多节点部署或需要重启后仍可查询时，
/// 提供自己的 Bean 即可覆盖：
///
/// ```java
/// @ApplicationScoped
/// public class RedisExcelExportTaskStore implements ExcelExportTaskStore { ... }
/// ```
public interface ExcelExportTaskStore {

    /// 写入或更新任务快照。
    void save(ExcelExportTask task);

    /// 按 ID 查询任务。
    Optional<ExcelExportTask> find(String id);

    /// 删除任务；不存在时静默返回。
    void delete(String id);
}
