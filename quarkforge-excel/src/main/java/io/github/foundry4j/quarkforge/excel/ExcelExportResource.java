package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.web.file.FileDownloads;
import io.github.foundry4j.quarkforge.web.file.FileStorage;
import io.github.foundry4j.quarkforge.web.file.StoredFile;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/// ## ExcelExportResource
/// 异步导出任务的查询与下载端点：
///
/// | 方法 | 路径 | 说明 |
/// |------|------|------|
/// | `GET` | `/excel/exports/{id}` | 任务状态与进度 |
/// | `GET` | `/excel/exports/{id}/file` | 下载结果文件 |
/// | `DELETE` | `/excel/exports/{id}` | 删除任务与结果文件 |
///
/// 提交动作由业务代码调用 [ExcelExportTaskService#submit] 完成，
/// 因为导出配置里的列取值函数无法通过 HTTP 反序列化。
@Path("/excel/exports")
public class ExcelExportResource {

    @Inject
    ExcelExportTaskService tasks;

    @Inject
    FileStorage storage;

    @GET
    @Path("/{id}")
    public ExcelExportTask status(@PathParam("id") String id) {
        return tasks.status(id);
    }

    @GET
    @Path("/{id}/file")
    @Blocking
    public Response file(@PathParam("id") String id) {
        StoredFile file = tasks.file(id);
        return FileDownloads.download(storage, file.key(), file.originalFilename(), file.contentType());
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id) {
        tasks.delete(id);
    }
}
