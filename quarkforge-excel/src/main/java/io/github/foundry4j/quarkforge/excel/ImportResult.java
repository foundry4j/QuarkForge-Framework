package io.github.foundry4j.quarkforge.excel;

import java.util.List;

/// ## ImportResult
/// 导入结果：成功/失败计数与错误行明细。
///
/// 错误列表默认最多保留 [ExcelImportSpec#maxErrors()] 条，超出时
/// `errorsTruncated=true`，但扫描仍会继续，计数始终准确。
///
/// @param totalRows       扫描到的数据行总数
/// @param successRows     通过映射与校验的行数
/// @param failedRows      存在错误的行数
/// @param errors          错误明细（可能被截断）
/// @param errorsTruncated 错误明细是否因上限被截断
public record ImportResult(long totalRows, long successRows, long failedRows,
                           List<RowError> errors, boolean errorsTruncated) {

    public ImportResult {
        errors = errors == null
                ? List.of()
                : List.copyOf(errors);
    }

    public boolean hasErrors() {
        return failedRows > 0;
    }
}
