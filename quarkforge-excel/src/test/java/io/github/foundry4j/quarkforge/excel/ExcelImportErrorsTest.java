package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.web.file.StoredFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImportErrorsTest {

    private static ImportResult result(RowError... errors) {
        return new ImportResult(3, 1, errors.length, List.of(errors), false);
    }

    @Test
    void writesCsvReportWithHeaderAndRows() {
        ImportResult result = result(
                new RowError(2, "age", "abc", "必须是数字", List.of("1", "abc")),
                new RowError(3, null, null, "行映射失败", List.of("2", "bob")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelImportErrors.write(out, result, ExcelFormat.CSV);
        String csv = out.toString(StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("行号"));
        assertTrue(csv.contains("必须是数字"));
        assertTrue(csv.contains("1 | abc"));
        assertEquals(3, csv.lines().count());
    }

    @Test
    void storesXlsxReportReadableByTheReader() {
        ExcelTestSupport.MemoryFileStorage storage = new ExcelTestSupport.MemoryFileStorage();
        ImportResult result = result(new RowError(2, "age", "abc", "必须是数字", List.of("1", "abc")));

        StoredFile file = ExcelImportErrors.store(storage, result, ExcelFormat.XLSX);

        assertNotNull(file);
        assertEquals("import-errors.xlsx", file.originalFilename());
        List<RowData> rows = new ArrayList<>();
        ExcelReader.read(new ByteArrayInputStream(storage.bytes(file.key())), ExcelFormat.XLSX,
                RowReadOptions.defaults(), rows::add);
        assertEquals(1, rows.size());
        assertEquals("2", rows.getFirst().cell("行号"));
        assertEquals("必须是数字", rows.getFirst().cell("错误说明"));
    }

    @Test
    void skipsReportWhenThereAreNoErrors() {
        ExcelTestSupport.MemoryFileStorage storage = new ExcelTestSupport.MemoryFileStorage();

        assertNull(ExcelImportErrors.store(storage, result(), ExcelFormat.XLSX));
    }

    @Test
    void writesHeaderOnlyWhenThereAreNoErrors() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelImportErrors.write(out, result(), ExcelFormat.CSV);

        assertEquals(1, out.toString(StandardCharsets.UTF_8).lines().count());
    }
}
