package io.github.foundry4j.quarkforge.data;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.context.ActorContext;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;

/// ## SoftDeleteRepository
/// 软删除仓储基类，默认过滤已删除数据，并提供回收站与恢复能力。
///
/// ```java
/// @ApplicationScoped
/// public class OrderRepository extends SoftDeleteRepository<Order> { }
/// ```
///
/// - [findAll]、[findById]、[count]、[deleteById]、[deleteAll] 都只作用于未删除数据
/// - [findAllIncludingDeleted] / [restoreById] 用于回收站
/// - 分页查询使用继承的 `page()` / `pageAll()`，同样默认过滤
///
/// 注意：直接调用 Panache 的 `find("...")` 或原生 SQL 不会自动带上过滤条件，
/// 需要自行追加 `deleted = false`。
///
/// @param <T> 软删除实体类型
public abstract class SoftDeleteRepository<T extends SoftDeleteEntity> extends BaseRepository<T> {

    private static final String ACTIVE_CONDITION = "deleted = false";

    @Override
    public PanacheQuery<T> findAll() {
        return find(ACTIVE_CONDITION);
    }

    @Override
    public T findById(Long id) {
        return find("id = ?1 and " + ACTIVE_CONDITION, id).firstResultOptional().orElse(null);
    }

    @Override
    public long count() {
        return count(ACTIVE_CONDITION);
    }

    /// 回收站：包含已删除数据。
    public PanacheQuery<T> findAllIncludingDeleted() {
        return find("id is not null");
    }

    /// 回收站：按 ID 查询，包含已删除数据。
    public T findByIdIncludingDeleted(Long id) {
        return find("id = ?1", id).firstResultOptional().orElse(null);
    }

    /// 回收站分页：包含已删除数据。
    public PageResult<T> pageAllIncludingDeleted(int page, int size) {
        return page(find("id is not null"), page, size);
    }

    /// 软删除指定 ID，返回是否命中未删除记录。
    /// `deleteVersion` 取自身主键，保证同一业务值可以被多次删除而不撞唯一索引。
    @Transactional
    public boolean deleteById(Long id) {
        return update("deleted = true, deletedAt = ?1, deletedBy = ?2, deleteVersion = id "
                        + "where id = ?3 and " + ACTIVE_CONDITION,
                Instant.now(), ActorContext.currentOr("system"), id) > 0;
    }

    /// 软删除实体，操作人取 [ActorContext]。
    @Override
    @Transactional
    public void delete(T entity) {
        softDelete(entity);
    }

    /// 软删除实体并指定操作人。
    @Transactional
    public void softDelete(T entity) {
        if (entity == null) {
            return;
        }
        entity.softDelete();
        if (!isPersistent(entity)) {
            getEntityManager().merge(entity);
        }
    }

    /// 软删除全部未删除数据。
    @Override
    @Transactional
    public long deleteAll() {
        return update("deleted = true, deletedAt = ?1, deletedBy = ?2, deleteVersion = id where " + ACTIVE_CONDITION,
                Instant.now(), ActorContext.currentOr("system"));
    }

    /// 按条件批量软删除（仅作用于未删除数据），返回是否命中记录。
    ///
    /// 字段名经元模型校验，匹配行被置为已删除（`deleteVersion` 取自身主键），
    /// 与 [deleteAll] 一致：批量 update 不触发实体生命周期回调，不会产生审计记录。
    @Override
    @Transactional
    public boolean deleteBy(String field, Object value) {
        resolveAttribute(field);
        return update("deleted = true, deletedAt = ?1, deletedBy = ?2, deleteVersion = id "
                        + "where " + field + " = ?3 and " + ACTIVE_CONDITION,
                Instant.now(), ActorContext.currentOr("system"), value) > 0;
    }

    /// 从回收站恢复，返回是否命中已删除记录。
    ///
    /// 恢复会把 `deleteVersion` 归零，若业务唯一键已被其他未删除数据占用，
    /// 数据库唯一约束会拒绝本次恢复，这里转换为 `CONFLICT` 业务异常。
    @Transactional
    public boolean restoreById(Long id) {
        try {
            return update("deleted = false, deletedAt = null, deletedBy = null, deleteVersion = 0 "
                    + "where id = ?1 and deleted = true", id) > 0;
        } catch (PersistenceException e) {
            throw new BizException(CommonErrorCode.CONFLICT, "恢复失败：业务唯一键已被其他数据占用");
        }
    }

    /// 物理删除指定 ID（谨慎使用）。
    @Transactional
    public boolean purgeById(Long id) {
        return delete("id = ?1", id) > 0;
    }

    /// 物理删除全部数据（谨慎使用）。
    @Transactional
    public long purgeAll() {
        return delete("id is not null");
    }
}
