package io.github.foundry4j.quarkforge.data;

import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## SoftDeleteUniqueIndexTest
/// `(业务列, delete_version)` 唯一索引下，软删除数据不再占用业务唯一键：
/// 删除后可用同一业务值重新创建、多次删除互不冲突、恢复冲突转为业务异常。
@QuarkusTest
class SoftDeleteUniqueIndexTest {

    private static final String EMAIL = "alice@example.com";

    @Inject
    UniqueUserRepository repository;

    @Inject
    EntityManager em;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(repository::purgeAll);
    }

    @Test
    void allowsRecreatingSameValueAfterSoftDelete() {
        Long deletedId = create(EMAIL);
        assertTrue(QuarkusTransaction.requiringNew().call(() -> repository.deleteById(deletedId)));

        Long newId = create(EMAIL);

        List<UniqueUser> active = QuarkusTransaction.requiringNew()
                .call(() -> repository.findAll().list());
        assertEquals(1, active.size());
        assertEquals(newId, active.getFirst().id);
        assertEquals(0, active.getFirst().deleteVersion);
    }

    @Test
    void allowsDeletingSameValueMultipleTimes() {
        Long first = create(EMAIL);
        QuarkusTransaction.requiringNew().call(() -> repository.deleteById(first));
        Long second = create(EMAIL);
        QuarkusTransaction.requiringNew().call(() -> repository.deleteById(second));

        List<UniqueUser> all = QuarkusTransaction.requiringNew()
                .call(() -> repository.findAllIncludingDeleted().list());
        assertEquals(2, all.size());
        assertEquals(2, all.stream().map(user -> user.deleteVersion).distinct().count());
        assertTrue(all.stream().allMatch(user -> user.deleteVersion > 0));
    }

    @Test
    void deleteAllAssignsDistinctDeleteVersions() {
        Long first = create(EMAIL);
        QuarkusTransaction.requiringNew().call(() -> repository.deleteById(first));
        create(EMAIL);
        create(EMAIL + ".cn");

        long affected = QuarkusTransaction.requiringNew().call(repository::deleteAll);

        assertEquals(2, affected);
        List<UniqueUser> all = QuarkusTransaction.requiringNew()
                .call(() -> repository.findAllIncludingDeleted().list());
        assertEquals(3, all.size());
        assertEquals(3, all.stream().map(user -> user.deleteVersion).distinct().count());
    }

    @Test
    void restoreResetsDeleteVersion() {
        Long id = create(EMAIL);
        QuarkusTransaction.requiringNew().call(() -> repository.deleteById(id));

        assertTrue(QuarkusTransaction.requiringNew().call(() -> repository.restoreById(id)));

        UniqueUser restored = QuarkusTransaction.requiringNew()
                .call(() -> repository.findById(id));
        assertFalse(restored.deleted);
        assertEquals(0, restored.deleteVersion);
    }

    @Test
    void restoreFailsWhenUniqueValueIsTaken() {
        Long deletedId = create(EMAIL);
        QuarkusTransaction.requiringNew().call(() -> repository.deleteById(deletedId));
        create(EMAIL);

        BizException error = assertThrows(BizException.class,
                () -> QuarkusTransaction.requiringNew().call(() -> repository.restoreById(deletedId)));

        assertEquals(40900, error.errorCode().code());
    }

    private Long create(String email) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UniqueUser user = new UniqueUser();
            user.email = email;
            em.persist(user);
            em.flush();
            return user.id;
        });
    }
}
