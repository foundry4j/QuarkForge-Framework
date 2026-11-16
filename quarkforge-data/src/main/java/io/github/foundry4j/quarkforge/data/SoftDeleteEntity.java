package io.github.foundry4j.quarkforge.data;

import io.github.foundry4j.quarkforge.core.context.ActorContext;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

/// ## SoftDeleteEntity
/// 软删除实体基类。删除只写入标记，数据仍保留：
///
/// - `deleted`：是否已删除，列表默认过滤
/// - `deletedAt` / `deletedBy`：删除时间与操作人
/// - `deleteVersion`：唯一索引辅助列，未删除为 0，删除后等于自身主键
///
/// ### 软删除与唯一索引冲突
/// 已删除的行仍占用唯一键，重复插入相同业务值会失败。做法是把 `deleteVersion`
/// 加入唯一索引：未删除行恒为 0（彼此仍唯一），已删除行取自身主键（互不冲突）。
///
/// ```java
/// @Entity
/// @Table(uniqueConstraints = @UniqueConstraint(
///         name = "uk_user_email", columnNames = {"email", "delete_version"}))
/// public class User extends SoftDeleteEntity {
///     public String email;
/// }
/// ```
///
/// 已有表需要补列（示例）：
///
/// ```sql
/// ALTER TABLE qf_user ADD COLUMN delete_version BIGINT NOT NULL DEFAULT 0;
/// ALTER TABLE qf_user ADD CONSTRAINT uk_user_email UNIQUE (email, delete_version);
/// ```
///
/// PostgreSQL 等支持部分索引的数据库也可以不改唯一键，直接建带条件的唯一索引：
///
/// ```sql
/// CREATE UNIQUE INDEX uk_user_email_active ON qf_user (email) WHERE deleted = false;
/// ```
///
/// 注意：恢复（`restore`）会把 `deleteVersion` 归零，若此时业务值已被其他未删除数据占用，
/// 数据库会拒绝恢复，[SoftDeleteRepository.restoreById] 会将其转换为冲突业务异常。
///
/// 配合 [SoftDeleteRepository] 使用，可直接获得「默认只看未删除数据、支持回收站恢复」：
///
/// ```java
/// @Entity
/// public class Order extends SoftDeleteEntity {
///     public String orderNo;
/// }
///
/// @ApplicationScoped
/// public class OrderRepository extends SoftDeleteRepository<Order> { }
///
/// orderRepository.deleteById(id);          // 软删除
/// orderRepository.findAll();               // 自动排除已删除
/// orderRepository.findAllIncludingDeleted(); // 回收站
/// orderRepository.restoreById(id);         // 恢复
/// ```
@MappedSuperclass
public abstract class SoftDeleteEntity extends BaseEntity {

    /// 是否已删除。
    @Column(name = "deleted", nullable = false)
    public boolean deleted;

    /// 删除时间，未删除时为 `null`。
    @Column(name = "deleted_at")
    public Instant deletedAt;

    /// 删除操作人。
    @Column(name = "deleted_by", length = 64)
    public String deletedBy;

    /// 唯一索引辅助列：未删除为 0，删除后取自身主键，避免软删除数据占用业务唯一键。
    @Column(name = "delete_version", nullable = false)
    public long deleteVersion;

    /// 是否已软删除。
    public boolean isDeleted() {
        return deleted;
    }

    /// 软删除，操作人取 [ActorContext]，未设置时为 `system`。
    public void softDelete() {
        softDelete(ActorContext.currentOr("system"));
    }

    /// 软删除并指定操作人。
    public void softDelete(String operator) {
        if (deleted) {
            return;
        }
        deleted = true;
        deletedAt = Instant.now();
        deletedBy = operator == null || operator.isBlank() ? "system" : operator;
        deleteVersion = id == null ? 0 : id;
    }

    /// 从回收站恢复。
    public void restore() {
        deleted = false;
        deletedAt = null;
        deletedBy = null;
        deleteVersion = 0;
    }

    /// 覆盖 Panache 的实例删除方法，默认走软删除。
    @Override
    public void delete() {
        softDelete();
    }

    /// 物理删除（谨慎使用，仅用于数据清理）。
    public void purge() {
        Panache.getEntityManager().remove(this);
    }
}
