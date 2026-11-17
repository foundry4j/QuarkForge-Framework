package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.web.file.StoredFile;

import java.time.Instant;

/// ## ExcelExportTask
/// 异步导出任务快照。提交后由 [ExcelExportTaskService] 在虚拟线程中执行，
/// 期间通过 [ExcelExportTaskStore] 持续更新进度，调用方轮询本对象即可。
///
/// ```java
/// ExcelExportTask task = exportTasks.submit(spec, "orders.xlsx", service.streamAll());
/// // 轮询 GET /excel/exports/{id}，直到 status 为 SUCCEEDED，再下载 /file
/// ```
///
/// @param id            任务 ID
/// @param filename      下载使用的文件名
/// @param format        导出格式
/// @param status        当前状态
/// @param processedRows 已处理行数
/// @param totalRows     总行数；数据源不是 `Collection` 时为 `-1`（未知）
/// @param file          生成的文件元信息，未完成时为 `null`
/// @param message       失败原因，未失败时为 `null`
/// @param createdAt     提交时间
/// @param finishedAt    结束时间，未结束时为 `null`
public record ExcelExportTask(String id, String filename, ExcelFormat format, ExcelExportStatus status,
                              long processedRows, long totalRows, StoredFile file, String message,
                              Instant createdAt, Instant finishedAt) {

    /// 是否已结束（成功或失败）。
    public boolean finished() {
        return status == ExcelExportStatus.SUCCEEDED || status == ExcelExportStatus.FAILED;
    }

    ExcelExportTask running() {
        return new ExcelExportTask(id, filename, format, ExcelExportStatus.RUNNING,
                processedRows, totalRows, file, message, createdAt, null);
    }

    ExcelExportTask progress(long processed) {
        return new ExcelExportTask(id, filename, format, status,
                processed, totalRows, file, message, createdAt, finishedAt);
    }

    ExcelExportTask succeeded(StoredFile stored, Instant now) {
        return new ExcelExportTask(id, filename, format, ExcelExportStatus.SUCCEEDED,
                processedRows, totalRows, stored, null, createdAt, now);
    }

    ExcelExportTask failed(String reason, Instant now) {
        return new ExcelExportTask(id, filename, format, ExcelExportStatus.FAILED,
                processedRows, totalRows, null, reason, createdAt, now);
    }
}
