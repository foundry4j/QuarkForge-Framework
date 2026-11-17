package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.web.file.FileStorage;
import io.github.foundry4j.quarkforge.web.file.StoredFile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;

/// ## ExcelExportTaskService
/// 异步导出：提交任务后立即返回，导出在虚拟线程中执行，
/// 期间持续更新进度，完成后文件落到 [FileStorage] 供下载。
///
/// ```java
/// ExcelExportTask task = exportTasks.submit(spec, "orders.xlsx", service.streamAll());
/// // 轮询 task.id() 直到 finished()，再调用 file(id) 或 GET /excel/exports/{id}/file
/// ```
///
/// 说明：
///
/// - 任务状态由 [ExcelExportTaskStore] 保存，默认进程内存储，单机部署足够；
/// - 结果文件走 [FileStorage]，因此同样受 `quarkforge.web.file.max-size` 限制；
/// - 任务失败不会抛出到调用方，原因记录在 [ExcelExportTask#message()]。
@ApplicationScoped
public class ExcelExportTaskService {

    /// 每处理多少行刷新一次进度，避免逐行写存储。
    private static final int PROGRESS_STEP = 200;

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ExcelExportTaskStore tasks;
    private final FileStorage storage;

    @Inject
    public ExcelExportTaskService(ExcelExportTaskStore tasks, FileStorage storage) {
        this.tasks = tasks;
        this.storage = storage;
    }

    /// 提交导出任务并立即返回；`rows` 在后台线程中惰性消费，不要复用同一个迭代器。
    public <T> ExcelExportTask submit(ExcelExportSpec<T> spec, String filename, Iterable<? extends T> rows) {
        if (spec == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少导出配置");
        }
        if (rows == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少导出数据");
        }
        if (filename == null || filename.isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少下载文件名");
        }
        ExcelFormat format = ExcelFormat.fromFilename(filename);
        long totalRows = rows instanceof Collection<?> collection ? collection.size() : -1;
        ExcelExportTask task = new ExcelExportTask(UUID.randomUUID().toString(), filename, format,
                ExcelExportStatus.PENDING, 0, totalRows, null, null, Instant.now(), null);
        tasks.save(task);
        Thread.ofVirtual().name("excel-export-" + task.id()).start(() -> execute(task, spec, rows));
        return task;
    }

    /// 查询任务；不存在时返回 404。
    public ExcelExportTask status(String id) {
        return tasks.find(id)
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND, "导出任务不存在"));
    }

    /// 取导出结果文件；任务未成功时返回 404。
    public StoredFile file(String id) {
        StoredFile file = status(id).file();
        if (file == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "导出文件尚未生成");
        }
        return file;
    }

    /// 删除任务及其结果文件；不存在时静默返回。
    public void delete(String id) {
        tasks.find(id).ifPresent(task -> {
            if (task.file() != null) {
                storage.delete(task.file().key());
            }
            tasks.delete(id);
        });
    }

    private <T> void execute(ExcelExportTask task, ExcelExportSpec<T> spec, Iterable<? extends T> rows) {
        tasks.save(task.running());
        Path temp = null;
        try {
            temp = Files.createTempFile("quarkforge-export-", "." + suffix(task.format()));
            try (OutputStream out = Files.newOutputStream(temp)) {
                ExcelExporter.write(out, task.format(), spec, withProgress(rows, task.id()));
            }
            StoredFile stored;
            try (InputStream in = Files.newInputStream(temp)) {
                stored = storage.store(in, task.filename(), task.format().mediaType());
            }
            update(task.id(), current -> current.succeeded(stored, Instant.now()));
        } catch (Exception e) {
            Log.errorf(e, "异步导出失败 task=%s", task.id());
            update(task.id(), current -> current.failed(messageOf(e), Instant.now()));
        } finally {
            deleteQuietly(temp);
        }
    }

    /// 包一层计数迭代器，按固定步长刷新进度。
    private <T> Iterable<T> withProgress(Iterable<? extends T> rows, String taskId) {
        return () -> new Iterator<>() {

            private final Iterator<? extends T> source = rows.iterator();
            private long processed;

            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public T next() {
                T value = source.next();
                processed++;
                if (processed % PROGRESS_STEP == 0) {
                    update(taskId, current -> current.progress(processed));
                }
                return value;
            }
        };
    }

    private void update(String id, UnaryOperator<ExcelExportTask> change) {
        tasks.find(id).ifPresent(current -> tasks.save(change.apply(current)));
    }

    private static String messageOf(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static String suffix(ExcelFormat format) {
        return format.name().toLowerCase(Locale.ROOT);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException _) {
            // 临时文件清理失败不影响任务结果
        }
    }
}
