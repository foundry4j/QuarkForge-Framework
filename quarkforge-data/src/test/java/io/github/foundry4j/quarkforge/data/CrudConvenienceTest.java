package io.github.foundry4j.quarkforge.data;

import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NonUniqueResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## CrudConvenienceTest
/// [BaseRepository] 单字段便捷方法：查询、统计、分页、保存、删除、字段校验，
/// 以及 [SoftDeleteRepository] 对 `deleteBy` 的软删除覆写。
@QuarkusTest
class CrudConvenienceTest {

    @Inject
    CursorStockRepository stockRepository;

    @Inject
    SoftNoteRepository softNoteRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("delete from VersionedStock").executeUpdate();
            em.createQuery("delete from SoftNote").executeUpdate();
        });
    }

    @Test
    void findFirstByReturnsFirstMatchOrEmpty() {
        Long id = persistStock("alpha", 1);

        Optional<VersionedStock> found = tx(() -> stockRepository.findFirstBy("name", "alpha"));
        assertTrue(found.isPresent());
        assertEquals(id, found.get().id);

        assertTrue(tx(() -> stockRepository.findFirstBy("name", "missing")).isEmpty());
    }

    @Test
    void findByExpectsUniqueMatch() {
        persistStock("only", 1);
        assertEquals("only", tx(() -> stockRepository.findBy("name", "only")).orElseThrow().name);

        persistStock("dup", 1);
        persistStock("dup", 2);
        assertThrows(NonUniqueResultException.class,
                () -> tx(() -> stockRepository.findBy("name", "dup")));
    }

    @Test
    void listByFiltersAndSorts() {
        persistStock("b", 1);
        persistStock("a", 1);
        persistStock("c", 1);
        persistStock("a", 2); // quantity 不同，不参与结果

        List<String> unsorted = tx(() -> stockRepository.listBy("quantity", 1))
                .stream().map(s -> s.name).toList();
        assertEquals(3, unsorted.size());
        assertTrue(unsorted.containsAll(List.of("a", "b", "c")));

        List<String> sorted = tx(() -> stockRepository.listBy("quantity", 1, Sort.by("name")))
                .stream().map(s -> s.name).toList();
        assertEquals(List.of("a", "b", "c"), sorted);

        List<String> descending = tx(() -> stockRepository.listBy(
                "quantity", 1, Sort.by("name").descending()))
                .stream().map(s -> s.name).toList();
        assertEquals(List.of("c", "b", "a"), descending);
    }

    @Test
    void countAndExistsBy() {
        persistStock("x", 1);
        persistStock("x", 2);
        persistStock("y", 1);

        assertEquals(2, tx(() -> stockRepository.countBy("name", "x")));
        assertEquals(0, tx(() -> stockRepository.countBy("name", "z")));

        assertTrue(tx(() -> stockRepository.existsBy("name", "x")));
        assertFalse(tx(() -> stockRepository.existsBy("name", "z")));
    }

    @Test
    void pageByReturnsPagedResultAndSorts() {
        for (int i = 0; i < 5; i++) {
            persistStock("item", i);
        }

        PageResult<VersionedStock> first = tx(() ->
                stockRepository.pageBy("name", "item", 1, 2, Sort.by("quantity")));
        assertEquals(2, first.records().size());
        assertEquals(5, first.total());
        assertEquals(1, first.page());
        assertEquals(0, first.records().getFirst().quantity);

        PageResult<VersionedStock> second = tx(() ->
                stockRepository.pageBy("name", "item", 2, 2, Sort.by("quantity")));
        assertEquals(2, second.records().size());
        assertEquals(2, second.records().getFirst().quantity);
    }

    @Test
    void requireByReturnsEntityOrThrowsNotFound() {
        Long id = persistStock("need", 1);

        VersionedStock found = tx(() -> stockRepository.requireBy("name", "need"));
        assertEquals(id, found.id);

        BizException error = assertThrows(BizException.class,
                () -> tx(() -> stockRepository.requireBy("name", "absent")));
        assertEquals(40400, error.errorCode().code());
    }

    @Test
    void savePersistsNewAndMergesExisting() {
        VersionedStock created = tx(() -> {
            VersionedStock stock = new VersionedStock();
            stock.name = "new";
            stock.quantity = 7;
            return stockRepository.save(stock);
        });
        assertNotNull(created.id);
        assertEquals("new", tx(() -> stockRepository.findById(created.id)).name);

        // 用一个游离实例（带 id）触发 merge
        tx(() -> {
            VersionedStock detached = new VersionedStock();
            detached.id = created.id;
            detached.name = "merged";
            detached.quantity = 9;
            detached.version = 0;
            return stockRepository.save(detached);
        });
        VersionedStock updated = tx(() -> stockRepository.findById(created.id));
        assertEquals("merged", updated.name);
        assertEquals(9, updated.quantity);
        assertTrue(updated.version > 0);
    }

    @Test
    void deleteByPhysicallyRemovesOnPlainRepository() {
        Long keep = persistStock("keep", 1);
        persistStock("drop", 1);

        boolean removed = tx(() -> stockRepository.deleteBy("name", "drop"));
        assertTrue(removed);

        assertEquals(1, tx(() -> stockRepository.count()));
        assertEquals(keep, tx(() -> stockRepository.findFirstBy("name", "keep")).orElseThrow().id);
        assertFalse(tx(() -> stockRepository.existsBy("name", "drop")));

        // 删除不存在的值返回 false
        assertFalse(tx(() -> stockRepository.deleteBy("name", "ghost")));
    }

    @Test
    void deleteBySoftDeletesOnSoftDeleteRepository() {
        Long keep = persistSoftNote("keep");
        Long drop = persistSoftNote("drop");

        boolean removed = tx(() -> softNoteRepository.deleteBy("title", "drop"));
        assertTrue(removed);

        // 默认视图：已删除行不可见
        assertEquals(1, tx(() -> softNoteRepository.count()));
        assertNull(tx(() -> softNoteRepository.findById(drop)));
        assertEquals(keep, tx(() -> softNoteRepository.findFirstBy("title", "keep")).orElseThrow().id);

        // 回收站：数据仍在，标记为已删除
        assertEquals(2, tx(() -> softNoteRepository.findAllIncludingDeleted().list().size()));
        SoftNote deleted = tx(() -> softNoteRepository.findByIdIncludingDeleted(drop));
        assertNotNull(deleted);
        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.deletedAt);
    }

    @Test
    void rejectsInvalidFieldWithParamError() {
        assertParamError(() -> stockRepository.findFirstBy("notAField", 1));
        assertParamError(() -> stockRepository.findBy("notAField", 1));
        assertParamError(() -> stockRepository.listBy("notAField", 1));
        assertParamError(() -> stockRepository.listBy("notAField", 1, Sort.by("name")));
        assertParamError(() -> stockRepository.countBy("notAField", 1));
        assertParamError(() -> stockRepository.existsBy("notAField", 1));
        assertParamError(() -> stockRepository.pageBy("notAField", 1, 1, 10));
        assertParamError(() -> stockRepository.pageBy("notAField", 1, 1, 10, Sort.by("name")));
        assertParamError(() -> stockRepository.requireBy("notAField", 1));
        assertParamError(() -> stockRepository.deleteBy("notAField", 1));
    }

    private void assertParamError(Runnable action) {
        BizException error = assertThrows(BizException.class,
                () -> QuarkusTransaction.requiringNew().run(action));
        assertEquals(40000, error.errorCode().code());
    }

    private Long persistStock(String name, int quantity) {
        return QuarkusTransaction.requiringNew().call(() -> {
            VersionedStock stock = new VersionedStock();
            stock.name = name;
            stock.quantity = quantity;
            stockRepository.persist(stock);
            return stock.id;
        });
    }

    private Long persistSoftNote(String title) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SoftNote note = new SoftNote();
            note.title = title;
            softNoteRepository.persist(note);
            return note.id;
        });
    }

    private <R> R tx(java.util.function.Supplier<R> action) {
        return QuarkusTransaction.requiringNew().call(action::get);
    }
}
