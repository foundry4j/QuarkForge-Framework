package io.github.foundry4j.quarkforge.excel;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// ## InMemoryExcelExportTaskStore
/// 默认的任务存储：进程内 `ConcurrentHashMap`，重启后任务丢失。
/// 单机部署足够；多节点请用 Redis 等实现覆盖。
@DefaultBean
@ApplicationScoped
public class InMemoryExcelExportTaskStore implements ExcelExportTaskStore {

    private final Map<String, ExcelExportTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(ExcelExportTask task) {
        tasks.put(task.id(), task);
    }

    @Override
    public Optional<ExcelExportTask> find(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public void delete(String id) {
        tasks.remove(id);
    }
}
