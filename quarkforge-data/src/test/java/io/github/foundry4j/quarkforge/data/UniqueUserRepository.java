package io.github.foundry4j.quarkforge.data;

import jakarta.enterprise.context.ApplicationScoped;

/// 软删除唯一索引测试用仓储。
@ApplicationScoped
public class UniqueUserRepository extends SoftDeleteRepository<UniqueUser> {
}
