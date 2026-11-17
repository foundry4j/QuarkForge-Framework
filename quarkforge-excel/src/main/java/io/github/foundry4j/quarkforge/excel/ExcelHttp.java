package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.web.file.FileDownloads;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.function.Consumer;

/// ## ExcelHttp
/// REST 层辅助：上传即导入、流式下载。
///
/// ```java
/// @POST
/// @Path("/import")
/// @Consumes(MediaType.MULTIPART_FORM_DATA)
/// public ImportResult importUsers(@RestForm FileUpload file) {
///     return ExcelHttp.importUpload(file, spec);
/// }
///
/// @GET
/// @Path("/export")
/// public Response exportOrders() {
///     return ExcelHttp.download("orders.xlsx", spec, service.streamAll());
/// }
/// ```
public final class ExcelHttp {

    private ExcelHttp() {
    }

    /// 按上传文件的 Content-Type / 文件名识别格式并流式导入。
    public static <T> ImportResult importUpload(FileUpload upload, ExcelImportSpec<T> spec) {
        if (upload == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少上传文件");
        }
        try (InputStream in = Files.newInputStream(upload.uploadedFile())) {
            return ExcelImporter.importData(in, formatOf(upload), spec);
        } catch (IOException e) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "读取上传文件失败", e);
        }
    }

    /// 按上传文件识别格式。
    public static ExcelFormat formatOf(FileUpload upload) {
        if (upload == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少上传文件");
        }
        return ExcelFormat.detect(upload.fileName(), upload.contentType());
    }

    /// 通用流式下载：`writer` 直接向响应输出流写数据，不经过内存缓冲。
    public static Response download(String filename, ExcelFormat format, Consumer<OutputStream> writer) {
        if (filename == null || filename.isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "缺少下载文件名");
        }
        StreamingOutput body = output -> {
            try {
                writer.accept(output);
            } catch (RuntimeException e) {
                throw new IOException(e.getMessage(), e);
            }
        };
        return Response.ok(body)
                .type(mediaType(format))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        FileDownloads.contentDisposition(filename, true))
                .build();
    }

    /// 导出 `Iterable` 数据并返回下载响应，格式由文件名后缀决定。
    public static <T> Response download(String filename, ExcelExportSpec<T> spec,
                                        Iterable<? extends T> rows) {
        ExcelFormat format = ExcelFormat.fromFilename(filename);
        return download(filename, format, out -> ExcelExporter.write(out, format, spec, rows));
    }

    /// 把导入错误明细作为文件直接下载（错误条数受 `maxErrors` 限制，适合小文件场景）。
    ///
    /// ```java
    /// ImportResult result = ExcelHttp.importUpload(file, spec);
    /// return ExcelHttp.downloadErrors(result, ExcelFormat.XLSX);
    /// ```
    public static Response downloadErrors(ImportResult result, ExcelFormat format) {
        return download(ExcelImportErrors.filename(format), format,
                out -> ExcelImportErrors.write(out, result, format));
    }

    private static MediaType mediaType(ExcelFormat format) {
        if (format == ExcelFormat.CSV) {
            return MediaType.valueOf("text/csv;charset=UTF-8");
        }
        return MediaType.valueOf(format.mediaType());
    }
}
