package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.web.file.StoredFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ExcelExportTaskServiceTest {

    record ExportRow(int id, String name) {
    }

    @Test
    void exportsInBackgroundAndReportsProgress() throws Exception {
        ExcelTestSupport.MemoryFileStorage storage = new ExcelTestSupport.MemoryFileStorage();
        ExcelExportTaskService service = service(storage);
        List<ExportRow> rows = IntStream.rangeClosed(1, 1200)
                .mapToObj(i -> new ExportRow(i, "user-" + i))
                .toList();

        ExcelExportTask submitted = service.submit(spec(), "users.csv", rows);

        assertEquals(ExcelExportStatus.PENDING, submitted.status());
        assertEquals(1200, submitted.totalRows());

        ExcelExportTask done = await(service, submitted.id());
        assertEquals(ExcelExportStatus.SUCCEEDED, done.status(), done::message);
        assertEquals(1200, done.processedRows());

        StoredFile file = service.file(submitted.id());
        assertNotNull(file);
        String csv = new String(storage.bytes(file.key()), StandardCharsets.UTF_8);
        assertEquals(1201, csv.lines().count());
        assertTrue(csv.contains("user-1200"));
    }

    @Test
    void marksTaskFailedWhenExportThrows() throws Exception {
        ExcelExportTaskService service = service(new ExcelTestSupport.MemoryFileStorage());
        ExcelExportSpec<ExportRow> broken = ExcelExportSpec.<ExportRow>builder("Users")
                .column("id", _ -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        ExcelExportTask submitted = service.submit(broken, "users.xlsx", List.of(new ExportRow(1, "a")));
        ExcelExportTask done = await(service, submitted.id());

        assertEquals(ExcelExportStatus.FAILED, done.status());
        assertTrue(done.message().contains("boom"));
        assertNull(done.file());
        assertThrows(BizException.class, () -> service.file(submitted.id()));
    }

    @Test
    void deleteRemovesTaskAndResultFile() throws Exception {
        ExcelTestSupport.MemoryFileStorage storage = new ExcelTestSupport.MemoryFileStorage();
        ExcelExportTaskService service = service(storage);
        ExcelExportTask submitted = service.submit(spec(), "users.csv", List.of(new ExportRow(1, "a")));

        ExcelExportTask done = await(service, submitted.id());
        String key = done.file().key();
        assertTrue(storage.exists(key));

        service.delete(submitted.id());

        assertFalse(storage.exists(key));
        BizException error = assertThrows(BizException.class, () -> service.status(submitted.id()));
        assertEquals(CommonErrorCode.NOT_FOUND.code(), error.code());
    }

    @Test
    void rejectsSubmissionWithoutFilename() {
        ExcelExportTaskService service = service(new ExcelTestSupport.MemoryFileStorage());
        assertThrows(BizException.class, () -> service.submit(spec(), " ", List.of()));
    }

    private static ExcelExportTaskService service(ExcelTestSupport.MemoryFileStorage storage) {
        return new ExcelExportTaskService(new InMemoryExcelExportTaskStore(), storage);
    }

    private static ExcelExportSpec<ExportRow> spec() {
        return ExcelExportSpec.<ExportRow>builder("Users")
                .column("id", ExportRow::id, 12)
                .column("name", ExportRow::name, 24)
                .build();
    }

    private static ExcelExportTask await(ExcelExportTaskService service, String id) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ExcelExportTask task = service.status(id);
            if (task.finished()) {
                return task;
            }
            Thread.sleep(Duration.ofMillis(20));
        }
        fail("导出任务未在超时内结束");
        return null;
    }
}
