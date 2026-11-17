package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// ## ExcelReader
/// 流式读取 `.xlsx` / `.csv`，逐行回调，内存占用与文件大小无关：
///
/// - Excel 走 Apache Fesod 的 SAX 读取器，不构建 Workbook
/// - CSV 使用字符级状态机，支持引号包裹的逗号/换行、`""` 转义、UTF-8 BOM
///
/// ```java
/// ExcelReader.read(in, ExcelFormat.XLSX, RowReadOptions.defaults(), row -> {
///     System.out.println(row.cell("name"));
/// });
/// ```
public final class ExcelReader {

    private ExcelReader() {
    }

    /// 按文件名后缀识别格式后读取。
    public static void read(InputStream in, String filename, RowReadOptions options, RowConsumer consumer) {
        read(in, ExcelFormat.fromFilename(filename), options, consumer);
    }

    /// 流式读取，`consumer` 每收到一行调用一次。
    public static void read(InputStream in, ExcelFormat format, RowReadOptions options, RowConsumer consumer) {
        if (in == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少待读取的文件流");
        }
        if (format == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少文件格式");
        }
        RowReadOptions opts = options == null ? RowReadOptions.defaults() : options;
        RowConsumer target = consumer == null ? _ -> {
        } : consumer;
        try {
            switch (format) {
                case XLSX -> readXlsx(in, opts, target);
                case CSV -> readCsv(in, opts, target);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.PARAM_ERROR,
                    "读取 " + format + " 文件失败", e);
        }
    }

    // ---------- Excel（Fesod SAX） ----------

    /// 关闭表头识别，交给我们自己的回调处理表头与空行，
    /// 这样 `headerRowIndex` 可以指向任意物理行，且未配置表头时首行也是数据。
    private static void readXlsx(InputStream in, RowReadOptions options, RowConsumer consumer) {
        FesodSheet.read(in)
                .headRowNumber(0)
                .ignoreEmptyRow(options.skipEmptyRows())
                .autoCloseStream(false)
                .registerReadListener(new SheetListener(options, consumer))
                .sheet(options.sheetIndex())
                .doRead();
    }

    /// Fesod 逐行回调：把 `Map<列号, 文本>` 归一化为定宽列表，并按需处理表头/空行。
    private static final class SheetListener implements ReadListener<Map<Integer, String>> {

        private final RowReadOptions options;
        private final RowConsumer consumer;
        private final List<String> headers = new ArrayList<>();

        private SheetListener(RowReadOptions options, RowConsumer consumer) {
            this.options = options;
            this.consumer = consumer;
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            int rowIndex = context.readRowHolder().getRowIndex();
            List<String> values = toValues(data);
            if (options.headerRowIndex() >= 0 && rowIndex == options.headerRowIndex()) {
                headers.clear();
                headers.addAll(values);
                return;
            }
            if (options.headerRowIndex() >= 0 && rowIndex < options.headerRowIndex()) {
                return;
            }
            if (options.skipEmptyRows() && isEmpty(values)) {
                return;
            }
            consumer.accept(new RowData(rowIndex + 1, headers, values));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 流式读取无需收尾动作
        }

        /// 缺失单元格补 `""`，保证列号与表头一一对应。
        private static List<String> toValues(Map<Integer, String> data) {
            if (data == null || data.isEmpty()) {
                return List.of();
            }
            int width = 0;
            for (Integer column : data.keySet()) {
                if (column != null) {
                    width = Math.max(width, column + 1);
                }
            }
            List<String> values = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                String value = data.get(i);
                values.add(value == null ? "" : value);
            }
            return values;
        }

        private static boolean isEmpty(List<String> values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
            return true;
        }
    }

    // ---------- CSV ----------

    private static void readCsv(InputStream in, RowReadOptions options, RowConsumer consumer) throws IOException {
        try (PushbackReader reader = new PushbackReader(
                new InputStreamReader(in, options.charset()), 2)) {
            int first = reader.read();
            if (first != 0xFEFF && first != -1) {
                reader.unread(first);
            }
            new CsvParser(options, consumer).parse(reader);
        }
    }

    /// CSV 字符级状态机：流式解析，支持多行引号字段与 BOM。
    private static final class CsvParser {

        private final RowReadOptions options;
        private final RowConsumer consumer;
        private final List<String> headers = new ArrayList<>();

        private List<String> fields = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private boolean inQuotes;
        private boolean recordStarted;
        private int recordIndex;

        private CsvParser(RowReadOptions options, RowConsumer consumer) {
            this.options = options;
            this.consumer = consumer;
        }

        private void parse(PushbackReader reader) throws IOException {
            int c;
            while ((c = reader.read()) != -1) {
                char ch = (char) c;
                if (inQuotes) {
                    if (ch == '"') {
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                            recordStarted = true;
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.unread(next);
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                } else if (ch == '"') {
                    inQuotes = true;
                    recordStarted = true;
                } else if (ch == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                    recordStarted = true;
                } else if (ch == '\n') {
                    emitRecord();
                } else if (ch == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                    emitRecord();
                } else {
                    field.append(ch);
                    recordStarted = true;
                }
            }
            if (inQuotes) {
                throw new BizException(CommonErrorCode.PARAM_ERROR, "CSV 格式错误：存在未闭合的引号");
            }
            if (recordStarted || !fields.isEmpty() || !field.isEmpty()) {
                emitRecord();
            }
        }

        private void emitRecord() {
            fields.add(field.toString());
            field.setLength(0);
            int logicalRow = recordIndex + 1;

            if (options.headerRowIndex() >= 0 && recordIndex == options.headerRowIndex()) {
                headers.clear();
                headers.addAll(fields);
            } else if (options.headerRowIndex() < 0 || recordIndex > options.headerRowIndex()) {
                if (!(options.skipEmptyRows() && isEmpty(fields))) {
                    consumer.accept(new RowData(logicalRow, headers, fields));
                }
            }

            fields = new ArrayList<>();
            recordIndex++;
            recordStarted = false;
        }

        private static boolean isEmpty(List<String> values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
            return true;
        }
    }
}
