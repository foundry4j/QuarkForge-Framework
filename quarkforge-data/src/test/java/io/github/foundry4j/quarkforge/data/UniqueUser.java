package io.github.foundry4j.quarkforge.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/// 测试用实体：`(email, delete_version)` 唯一，验证软删除后同一邮箱可重复使用。
@Entity
@Table(name = "qf_unique_user", uniqueConstraints = @UniqueConstraint(
        name = "uk_unique_user_email", columnNames = {"email", "delete_version"}))
public class UniqueUser extends SoftDeleteEntity {

    public String email;
}
