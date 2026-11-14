package io.github.foundry4j.quarkforge.outbox.admin;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.outbox.OutboxMessage;
import io.github.foundry4j.quarkforge.outbox.OutboxRepository;
import io.github.foundry4j.quarkforge.outbox.OutboxStatus;
import io.github.foundry4j.quarkforge.web.api.PageQuery;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/// ## OutboxAdminService
/// 发件箱人工补偿能力：
///
/// - 按状态/目标分页查询消息，默认查询 `FAILED`；
/// - 单条重放：校验状态后重置为 `PENDING`，清空失败原因与重试次数；
/// - 按条件批量重放：目标地址、创建时间上限、条数上限。
///
/// 重放只是把消息交还给 [io.github.foundry4j.quarkforge.outbox.OutboxDispatcher]，
/// 实际投递仍由调度器完成，避免管理端点直接依赖消息中间件。
@ApplicationScoped
public class OutboxAdminService {

    private static final int DEFAULT_BATCH_LIMIT = 200;
    private static final int MAX_BATCH_LIMIT = 1_000;

    @Inject
    OutboxRepository repository;

    /// 分页查询，`status` 为空时默认只查 `FAILED`。
    public PageResult<OutboxMessageView> list(OutboxStatus status, String destination, PageQuery page) {
        List<Object> params = new ArrayList<>();
        String where = where(status == null ? OutboxStatus.FAILED : status, destination, null, params);

        PanacheQuery<OutboxMessage> query = repository.find(where + " order by id desc", params.toArray());
        long total = query.count();
        List<OutboxMessageView> records = query.page(page.page() - 1, page.size()).list().stream()
                .map(OutboxMessageView::of)
                .toList();
        return new PageResult<>(records, total, page.page(), page.size());
    }

    /// 重放单条消息，仅允许 `FAILED` 状态。
    @Transactional
    public void replay(Long id) {
        OutboxMessage message = repository.findById(id);
        if (message == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "发件箱消息不存在");
        }
        if (message.status != OutboxStatus.FAILED) {
            throw new BizException(CommonErrorCode.CONFLICT, "仅 FAILED 状态的消息可以重放");
        }
        reset(List.of(id));
    }

    /// 按条件批量重放，返回本次重置的条数。
    @Transactional
    public int replayBatch(OutboxReplayRequest request) {
        String destination = request == null ? null : request.destination();
        Instant before = request == null ? null : request.before();
        int limit = limit(request == null ? null : request.limit());

        List<Object> params = new ArrayList<>();
        String where = where(OutboxStatus.FAILED, destination, before, params);
        List<Long> ids = repository.find(where + " order by id", params.toArray())
                .page(0, limit)
                .list()
                .stream()
                .map(message -> message.id)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        reset(ids);
        return ids.size();
    }

    private void reset(List<Long> ids) {
        Instant now = Instant.now();
        repository.update("status = ?1, attempts = 0, nextAttemptAt = ?2, lastError = null, updatedAt = ?2 where id in ?3",
                OutboxStatus.PENDING, now, ids);
    }

    private static String where(OutboxStatus status, String destination, Instant before, List<Object> params) {
        StringBuilder where = new StringBuilder("status = ?1");
        params.add(status);
        if (destination != null && !destination.isBlank()) {
            params.add(destination);
            where.append(" and destination = ?").append(params.size());
        }
        if (before != null) {
            params.add(before);
            where.append(" and createdAt <= ?").append(params.size());
        }
        return where.toString();
    }

    private static int limit(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_BATCH_LIMIT;
        }
        return Math.min(requested, MAX_BATCH_LIMIT);
    }
}
