package io.github.foundry4j.quarkforge.excel;

import java.util.List;

/// ## RowData
/// 一行只读数据，由流式读取器逐行回调产生，不随文件整体驻留内存。
///
/// ```java
/// new RowData(2, List.of("id", "name"), List.of("1", "alice"))
/// ```
///
/// @param rowNumber 1 起始的行号（CSV 为逻辑记录号，Excel 为物理行号）
/// @param headers   表头名称；未配置表头时为空列表
/// @param values    单元格文本值，缺失单元格为 `""`
public record RowData(int rowNumber, List<String> headers, List<String> values) {

    public RowData {
        headers = headers == null
                ? List.of()
                : List.copyOf(headers);
        values = values == null
                ? List.of()
                : List.copyOf(values);
    }

    /// 按列序号取值（0 起始）；越界返回 `""`。
    public String cell(int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        String value = values.get(index);
        return value == null ? "" : value;
    }

    /// 按表头名称取值；表头不存在（或未配置表头）时返回 `""`。
    public String cell(String header) {
        int index = headers.indexOf(header);
        return index < 0 ? "" : cell(index);
    }

    /// 列数。
    public int size() {
        return values.size();
    }

    /// 整行是否全为空白。
    public boolean isEmpty() {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
