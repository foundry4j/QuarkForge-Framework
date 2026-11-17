package io.github.foundry4j.quarkforge.excel;

import java.util.List;

/// ## RowError
/// 单条导入错误，随 [ImportResult] 回传给调用方。
///
/// @param rowNumber 出错行号（与 [RowData#rowNumber()] 一致）
/// @param field     出错字段；映射/整行错误时为 `null`
/// @param value     出错单元格的原始值
/// @param message   可读的错误说明
/// @param rowValues 该行全部原始值，便于前端/导出回显
public record RowError(int rowNumber, String field, String value, String message, List<String> rowValues) {

    public RowError {
        rowValues = rowValues == null
                ? List.of()
                : List.copyOf(rowValues);
    }
}
