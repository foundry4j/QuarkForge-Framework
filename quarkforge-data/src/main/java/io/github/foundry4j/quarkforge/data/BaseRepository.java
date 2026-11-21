package io.github.foundry4j.quarkforge.data;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.CursorPage;
import io.github.foundry4j.quarkforge.core.api.PageResult;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.transaction.Transactional;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// ## BaseRepository
/// 仓储基类，在 Panache 标准能力之上补充：
///
/// - 分页查询并直接产出统一分页结构 [PageResult]
/// - 游标分页 [findPageAfter]，用于深分页（避免 `offset` 越翻越慢）
/// - `requireById`：查不到即抛 `NOT_FOUND` 业务异常，省去判空样板代码
/// - 单字段便捷查询（`findFirstBy` / `findBy` / `listBy` / `countBy` / `existsBy` /
///   `pageBy` / `requireBy` / `deleteBy` / `save`），字段名经 JPA 元模型校验
///
/// 用法：
///
/// ```java
/// @ApplicationScoped
/// public class UserRepository extends BaseRepository<User> {
///     public PageResult<User> search(String keyword, int page, int size) {
///         return page(find("name like ?1", "%" + keyword + "%"), page, size);
///     }
/// }
/// ```
///
/// @param <T> 实体类型
public abstract class BaseRepository<T> implements PanacheRepositoryBase<T, Long> {

    /// 游标分页单页上限。
    private static final int MAX_PAGE_SIZE = 500;

    /// 对任意 Panache 查询执行分页（`page` 从 1 开始）。
    public PageResult<T> page(PanacheQuery<T> query, int page, int size) {
        int pageIndex = Math.max(page, 1);
        return PageResult.of(query.page(Page.of(pageIndex - 1, size)).list(), query.count(), pageIndex, size);
    }

    /// 全表分页查询。
    public PageResult<T> pageAll(int page, int size) {
        return page(findAll(), page, size);
    }

    /// ## 单字段便捷查询
    ///
    /// 用「实体字段名 + 值」做精确匹配，省去每个仓库手写 `find("field = ?1", value)` 的样板。
    /// 字段名经 JPA 元模型校验（[validateField]），非法字段抛出 `40000`，
    /// 避免把请求参数直接拼进 SQL；`null` 值按 SQL 语义不会命中（判空请用后续的 `Conditions`）。

    /// 取第一条匹配记录，无匹配返回空。
    public Optional<T> findFirstBy(String field, Object value) {
        validateField(field);
        return find(field + " = ?1", value).firstResultOptional();
    }

    /// 取唯一匹配记录，无匹配返回空；多于一条时抛 `NonUniqueResultException`。
    public Optional<T> findBy(String field, Object value) {
        validateField(field);
        return find(field + " = ?1", value).singleResultOptional();
    }

    /// 列出全部匹配记录。
    public List<T> listBy(String field, Object value) {
        return listBy(field, value, null);
    }

    /// 列出全部匹配记录并按 [sort] 排序。
    public List<T> listBy(String field, Object value, Sort sort) {
        validateField(field);
        PanacheQuery<T> query = sort == null
                ? find(field + " = ?1", value)
                : find(field + " = ?1", sort, value);
        return query.list();
    }

    /// 统计匹配记录数。
    public long countBy(String field, Object value) {
        validateField(field);
        return count(field + " = ?1", value);
    }

    /// 是否存在匹配记录。
    public boolean existsBy(String field, Object value) {
        return countBy(field, value) > 0;
    }

    /// 单条件偏移分页（page 从 1 开始）。
    public PageResult<T> pageBy(String field, Object value, int page, int size) {
        return pageBy(field, value, page, size, null);
    }

    /// 单条件偏移分页并按 [sort] 排序。
    public PageResult<T> pageBy(String field, Object value, int page, int size, Sort sort) {
        validateField(field);
        PanacheQuery<T> query = sort == null
                ? find(field + " = ?1", value)
                : find(field + " = ?1", sort, value);
        return page(query, page, size);
    }

    /// 取唯一匹配记录，不存在时抛 `NOT_FOUND` 业务异常。
    public T requireBy(String field, Object value) {
        return findFirstBy(field, value)
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND));
    }

    /// 按条件批量删除，返回是否命中记录。
    ///
    /// 软删除仓储（[SoftDeleteRepository]）会覆写为批量软删除，仅作用于未删除数据。
    @Transactional
    public boolean deleteBy(String field, Object value) {
        validateField(field);
        return delete(field + " = ?1", value) > 0;
    }

    /// 持久化实体并返回：新实体（id 为空）执行 persist，已有 id 执行 merge。
    @Transactional
    public T save(T entity) {
        if (entity == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "实体不能为空");
        }
        if (identifierOf(entity) == null) {
            persist(entity);
            return entity;
        }
        return getEntityManager().merge(entity);
    }

    /// 按 ID 查询，不存在时抛出 `NOT_FOUND` 业务异常。
    public T requireById(Long id) {
        T entity = findById(id);
        if (entity == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /// 游标（keyset）分页：按 `排序字段 + id` 定位上一页末尾之后的数据。
    ///
    /// 与 `LIMIT/OFFSET` 相比，深页码时不会扫描并丢弃前面所有行；
    /// 排序字段需是非空、可比较的公共字段（如 `id`、`createdAt`），`id` 作为唯一性兜底。
    ///
    /// ```java
    /// CursorPage<User> first = userRepository.findPageAfter("createdAt", false, null, 20);
    /// CursorPage<User> next = userRepository.findPageAfter("createdAt", false, first.nextCursor(), 20);
    /// ```
    ///
    /// @param field     排序字段（实体公共字段名）
    /// @param ascending 是否升序
    /// @param cursor    上一页返回的游标，首次传 null
    /// @param size      每页条数，1~500
    /// @return 当前页与下一页游标
    public CursorPage<T> findPageAfter(String field, boolean ascending, String cursor, int size) {
        SingularAttribute<?, ?> sortAttribute = resolveAttribute(field);
        Class<?> valueType = sortAttribute.getJavaType();
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String direction = ascending ? "asc" : "desc";
        String comparator = ascending ? ">" : "<";

        List<Object> params = new ArrayList<>(2);
        StringBuilder query = new StringBuilder();
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Cursor decoded = CursorCodec.decode(cursor, valueType);
            query.append('(').append(field).append(' ').append(comparator).append(" ?1")
                    .append(" or (").append(field).append(" = ?1 and id ").append(comparator).append(" ?2)) ");
            params.add(decoded.value());
            params.add(decoded.id());
        }
        query.append("order by ").append(field).append(' ').append(direction)
                .append(", id ").append(direction);

        List<T> rows = find(query.toString(), params.toArray()).page(Page.of(0, limit + 1)).list();
        boolean hasMore = rows.size() > limit;
        List<T> records = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        if (!hasMore) {
            return CursorPage.of(records, null, false);
        }
        Object lastId = identifierOf(records.getLast());
        Object lastValue = readSortValue(field, valueType, lastId);
        return CursorPage.of(records, CursorCodec.encode(lastValue, lastId), true);
    }

    /// 解析仓储的实体类型，用于校验排序字段。
    @SuppressWarnings("unchecked")
    protected Class<T> entityClass() {
        Class<?> current = getClass();
        while (current != null && current != Object.class) {
            Type superType = current.getGenericSuperclass();
            if (superType instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> clazz
                    && clazz != Object.class) {
                return (Class<T>) clazz;
            }
            current = current.getSuperclass();
        }
        throw new BizException(CommonErrorCode.PARAM_ERROR, "无法解析仓储的实体类型");
    }

    /// 校验字段为实体上的标量属性（关联属性会被拒绝），非法抛出 `40000`。
    protected final void validateField(String field) {
        resolveAttribute(field);
    }

    /// 通过 JPA 元模型校验排序/查询字段：必须是实体上的标量属性
    /// （Quarkus 会增强实体字节码，字段反射不可靠，因此以元模型为准）。
    protected SingularAttribute<?, ?> resolveAttribute(String field) {
        if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "无效的排序字段: " + field);
        }
        EntityType<?> entityType = getEntityManager().getMetamodel().entity(entityClass());
        Attribute<?, ?> attribute;
        try {
            attribute = entityType.getAttribute(field);
        } catch (IllegalArgumentException e) {
            attribute = null;
        }
        if (!(attribute instanceof SingularAttribute<?, ?> singular) || singular.isAssociation()) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "不支持的排序字段: " + field);
        }
        return singular;
    }

    /// 读取最后一条记录的排序值，用于生成下一页游标。
    private Object readSortValue(String field, Class<?> valueType, Object id) {
        EntityType<?> entityType = getEntityManager().getMetamodel().entity(entityClass());
        return getEntityManager()
                .createQuery("select e." + field + " from " + entityType.getName() + " e where e.id = ?1", valueType)
                .setParameter(1, id)
                .getSingleResult();
    }

    private Object identifierOf(T entity) {
        return getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }
}
