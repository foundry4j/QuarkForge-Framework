package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.web.file.FileStorage;
import io.github.foundry4j.quarkforge.web.file.StoredFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/// ## ExcelImportErrors
/// 把 [ImportResult] 里的错误明细导出成可下载文件：
///
/// - [write] 直接写到输出流（适合一次性响应下载）
/// - [store] 写入 [FileStorage]，返回的 [StoredFile#key()] 可随导入结果回传，
///   前端稍后按 key 下载（适合"先返回导入结果和错误文件链接"的交互）
///
/// ```java
/// ImportResult result = ExcelHttp.importUpload(file, spec);
/// StoredFile report = ExcelImportErrors.store(storage, result, ExcelFormat.XLSX);
/// ```
public final class ExcelImportErrors {

    private ExcelImportErrors() {
    }

    /// 错误明细列定义：行号、字段、值、错误说明、原始行。
    public static ExcelExportSpec<RowError> spec() {
        return ExcelExportSpec.<RowError>builder("Import errors")
                .column("行号", RowError::rowNumber, 10)
                .column("字段", RowError::field, 16)
                .column("值", RowError::value, 32)
                .column("错误说明", RowError::message, 48)
                .column("原始行", row -> String.join(" | ", row.rowValues()), 48)
                .build();
    }

    /// 把错误明细写到输出流；没有错误时只写表头。
    public static void write(OutputStream out, ImportResult result, ExcelFormat format) {
        if (out == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少输出流");
        }
        ExcelExporter.write(out, format, spec(), errorsOf(result));
    }

    /// 把错误明细写入文件存储；没有错误行时返回 `null`。
    public static StoredFile store(FileStorage storage, ImportResult result, ExcelFormat format) {
        if (storage == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少文件存储");
        }
        List<RowError> errors = errorsOf(result);
        if (errors.isEmpty()) {
            return null;
        }
        String filename = filename(format);
        Path temp = null;
        try {
            temp = Files.createTempFile("quarkforge-import-errors-", "." + suffix(format));
            try (OutputStream out = Files.newOutputStream(temp)) {
                ExcelExporter.write(out, format, spec(), errors);
            }
            try (InputStream in = Files.newInputStream(temp)) {
                return storage.store(in, filename, format.mediaType());
            }
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "生成导入错误明细失败", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /// 错误明细默认文件名。
    public static String filename(ExcelFormat format) {
        return "import-errors." + suffix(format);
    }

    private static List<RowError> errorsOf(ImportResult result) {
        if (result == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少导入结果");
        }
        return result.errors();
    }

    private static String suffix(ExcelFormat format) {
        if (format == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少导出格式");
        }
        return format.name().toLowerCase(Locale.ROOT);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException _) {
            // 临时文件清理失败不影响结果
        }
    }
}
