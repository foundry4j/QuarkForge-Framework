package io.github.foundry4j.quarkforge.outbox.admin;

import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.api.Result;
import io.github.foundry4j.quarkforge.outbox.OutboxStatus;
import io.github.foundry4j.quarkforge.security.RequiresRole;
import io.github.foundry4j.quarkforge.web.api.PageQuery;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/// ## OutboxAdminResource
/// 发件箱管理端点，默认要求 `admin` 角色：
///
/// | 方法 | 路径 | 说明 |
/// |------|------|------|
/// | GET | `/outbox/admin/messages` | 查询消息，默认 `status=FAILED`，支持 `destination` 过滤 |
/// | POST | `/outbox/admin/messages/{id}/replay` | 重放单条失败消息 |
/// | POST | `/outbox/admin/messages/replay` | 按条件批量重放 |
///
/// 批量重放请求体示例：
///
/// ```json
/// {"destination": "order.created", "before": "2026-01-01T00:00:00Z", "limit": 100}
/// ```
@Path("/outbox/admin/messages")
@Produces(MediaType.APPLICATION_JSON)
@RequiresRole("admin")
public class OutboxAdminResource {

    @Inject
    OutboxAdminService service;

    @GET
    public Result<PageResult<OutboxMessageView>> list(@QueryParam("status") OutboxStatus status,
                                                      @QueryParam("destination") String destination,
                                                      @BeanParam PageQuery page) {
        return Result.ok(service.list(status, destination, page));
    }

    @POST
    @Path("/{id}/replay")
    public Result<Void> replay(@PathParam("id") Long id) {
        service.replay(id);
        return Result.ok();
    }

    @POST
    @Path("/replay")
    @Consumes(MediaType.APPLICATION_JSON)
    public Result<Integer> replayBatch(OutboxReplayRequest request) {
        return Result.ok(service.replayBatch(request));
    }
}
