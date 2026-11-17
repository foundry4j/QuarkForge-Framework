package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.enums.poi.FillPatternTypeEnum;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.fesod.sheet.write.style.HorizontalCellStyleStrategy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/// ## ExcelExporter
/// 流式导出，内存占用由批次大小控制，与总行数无关：
///
/// - `.xlsx` 使用 Apache Fesod 分批写出，每写满 `windowSize` 行刷出一次
/// - `.csv` 直接逐行写 `OutputStream`，并可选写 UTF-8 BOM
///
/// ```java
/// ExcelExporter.write(response, ExcelFormat.XLSX,
///         ExcelExportSpec.<Order>builder("Orders")
///                 .column("订单号", Order::orderNo)
///                 .column("金额", Order::amount)
///                 .build(),
///         orderStream);
/// ```
public final class ExcelExporter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /// `IndexedColors.GREY_25_PERCENT` 的调色板下标，用于表头底色。
    private static final short HEADER_FILL_COLOR = 22;

    private ExcelExporter() {
    }

    /// 按文件名后缀推断格式后导出。
    public static <T> void write(OutputStream out, String filename, ExcelExportSpec<T> spec,
                                 Iterable<? extends T> rows) {
        write(out, ExcelFormat.fromFilename(filename), spec, rows);
    }

    /// 流式导出 `Iterable` 数据。
    public static <T> void write(OutputStream out, ExcelFormat format, ExcelExportSpec<T> spec,
                                 Iterable<? extends T> rows) {
        if (out == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少输出流");
        }
        if (format == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少导出格式");
        }
        if (spec == null || rows == null) {
            throw new IllegalArgumentException("spec 与 rows 不能为空");
        }
        switch (format) {
            case XLSX -> writeXlsx(out, spec, rows);
            case CSV -> writeCsv(out, spec, rows);
        }
    }

    /// 流式导出 `Stream` 数据（不会主动关闭 stream）。
    public static <T> void write(OutputStream out, ExcelFormat format, ExcelExportSpec<T> spec,
                                 Stream<T> rows) {
        write(out, format, spec, rows::iterator);
    }

    // ---------- XLSX ----------

    private static <T> void writeXlsx(OutputStream out, ExcelExportSpec<T> spec,
                                      Iterable<? extends T> rows) {
        List<List<String>> head = spec.columns().stream()
                .map(column -> List.of(column.header()))
                .toList();
        ExcelWriter writer = FesodSheet.write(out)
                .head(head)
                .autoCloseStream(false)
                .registerWriteHandler(new HorizontalCellStyleStrategy(headerStyle(), new WriteCellStyle()))
                .registerWriteHandler(new LayoutHandler(spec))
                .build();
        try {
            WriteSheet sheet = FesodSheet.writerSheet(0, spec.sheetName()).build();
            List<List<Object>> batch = new ArrayList<>(spec.windowSize());
            int written = 0;
            for (T item : rows) {
                batch.add(rowOf(item, spec));
                if (batch.size() >= spec.windowSize()) {
                    writer.write(batch, sheet);
                    written += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty() || written == 0) {
                // 空数据也要写出表头
                writer.write(batch, sheet);
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出 Excel 失败", e);
        } finally {
            writer.finish();
        }
    }

    private static WriteCellStyle headerStyle() {
        WriteCellStyle style = new WriteCellStyle();
        WriteFont font = new WriteFont();
        font.setBold(true);
        style.setWriteFont(font);
        style.setFillForegroundColor(HEADER_FILL_COLOR);
        style.setFillPatternType(FillPatternTypeEnum.SOLID_FOREGROUND.getPoiFillPatternType());
        return style;
    }

    /// 按列定义取值，并把枚举/时间类型规整成 Fesod 内置转换器支持的形态。
    private static <T> List<Object> rowOf(T item, ExcelExportSpec<T> spec) {
        List<Object> values = new ArrayList<>(spec.columns().size());
        for (Column<T> column : spec.columns()) {
            values.add(normalize(column.getter().apply(item)));
        }
        return values;
    }

    private static Object normalize(Object value) {
        return switch (value) {
            case null -> null;
            case Enum<?> enumValue -> enumValue.name();
            case Instant instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            case java.sql.Date date -> date.toLocalDate();
            default -> value;
        };
    }

    /// 设置列宽与冻结表头：Fesod 未提供声明式入口，通过 `SheetWriteHandler` 落到工作表上。
    private record LayoutHandler(ExcelExportSpec<?> spec) implements SheetWriteHandler {

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            var sheet = sheetHolder.getSheet();
            for (int i = 0; i < spec.columns().size(); i++) {
                sheet.setColumnWidth(i, Math.clamp(spec.columns().get(i).width(), 8, 60) * 256);
            }
            if (spec.freezeHeader()) {
                sheet.createFreezePane(0, 1);
            }
        }
    }

    // ---------- CSV ----------

    private static <T> void writeCsv(OutputStream out, ExcelExportSpec<T> spec,
                                     Iterable<? extends T> rows) {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        try {
            if (spec.csvBom()) {
                writer.write('\uFEFF');
            }
            writeCsvLine(writer, spec.columns().stream()
                    .map(Column::header).map(Object.class::cast).toList());
            for (T item : rows) {
                Object[] values = new Object[spec.columns().size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = spec.columns().get(i).getter().apply(item);
                }
                writeCsvLine(writer, Arrays.asList(values));
            }
            writer.flush();
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "导出 CSV 失败", e);
        }
    }

    private static void writeCsvLine(Writer writer, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(csvValue(values.get(i))));
        }
        writer.write('\n');
    }

    private static String csvValue(Object value) {
        return switch (value) {
            case null -> "";
            case LocalDate date -> DATE_FORMAT.format(date);
            case LocalDateTime dateTime -> DATE_TIME_FORMAT.format(dateTime);
            case Instant instant -> DATE_TIME_FORMAT.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
            case Timestamp timestamp -> DATE_TIME_FORMAT.format(timestamp.toLocalDateTime());
            case java.sql.Date date -> DATE_FORMAT.format(date.toLocalDate());
            case java.util.Date date -> DATE_TIME_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
            case Enum<?> enumValue -> enumValue.name();
            default -> String.valueOf(value);
        };
    }

    private static String escapeCsv(String value) {
        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.startsWith(" ")
                || value.endsWith(" ");
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
