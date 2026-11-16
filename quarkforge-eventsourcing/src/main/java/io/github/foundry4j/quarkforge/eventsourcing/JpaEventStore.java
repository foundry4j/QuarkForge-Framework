package io.github.foundry4j.quarkforge.eventsourcing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/// ## JpaEventStore
/// 基于关系库的事件存储：事件负载以 JSON 存表，聚合内序号唯一约束兜住并发写入。
///
/// 追加时先比较期望版本，再插入 `expectedVersion + 1 ...` 序号；
/// 若两个实例同时通过版本检查，后提交者会撞唯一约束并转为
/// [OptimisticConcurrencyException]，调用方重载聚合后重试即可。
@ApplicationScoped
public class JpaEventStore implements EventStore {

    @Inject
    StoredEventRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public long versionOf(String aggregateType, String aggregateId) {
        return repository.count("aggregateType = ?1 and aggregateId = ?2", aggregateType, aggregateId);
    }

    @Override
    public List<DomainEvent> loadEvents(String aggregateType, String aggregateId) {
        List<StoredEventEntity> rows = repository
                .find("aggregateType = ?1 and aggregateId = ?2 order by sequenceNo", aggregateType, aggregateId)
                .list();
        List<DomainEvent> events = new ArrayList<>(rows.size());
        for (StoredEventEntity row : rows) {
            events.add(deserialize(row.eventType, row.payload));
        }
        return events;
    }

    @Override
    @Transactional
    public void append(String aggregateType, String aggregateId, long expectedVersion, List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        long actualVersion = versionOf(aggregateType, aggregateId);
        if (actualVersion != expectedVersion) {
            throw new OptimisticConcurrencyException(
                    "聚合已被并发修改: " + aggregateType + "/" + aggregateId
                            + " 期望版本 " + expectedVersion + "，实际版本 " + actualVersion);
        }
        long sequence = actualVersion;
        for (DomainEvent event : events) {
            StoredEventEntity row = new StoredEventEntity();
            row.aggregateType = aggregateType;
            row.aggregateId = aggregateId;
            row.sequenceNo = ++sequence;
            row.eventType = event.getClass().getName();
            row.payload = serialize(event);
            row.occurredAt = Instant.now();
            repository.persist(row);
        }
        try {
            // 立即 flush，把并发写入的唯一键冲突在本方法内暴露
            repository.flush();
        } catch (PersistenceException e) {
            throw new OptimisticConcurrencyException(
                    "聚合并发写入冲突: " + aggregateType + "/" + aggregateId, e);
        }
    }

    @Override
    public List<StoredEvent> readAll(long afterEventId, int limit) {
        return repository.find("id > ?1 order by id", afterEventId)
                .page(0, Math.max(1, limit))
                .list()
                .stream()
                .map(row -> new StoredEvent(row.id, row.aggregateType, row.aggregateId, row.sequenceNo,
                        row.eventType, deserialize(row.eventType, row.payload), row.occurredAt))
                .toList();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("事件序列化失败: " + event.getClass().getName(), e);
        }
    }

    private DomainEvent deserialize(String eventType, String payload) {
        try {
            Class<?> type = Class.forName(eventType);
            return (DomainEvent) objectMapper.readValue(payload, type);
        } catch (ClassNotFoundException | JsonProcessingException e) {
            throw new IllegalStateException("事件反序列化失败: " + eventType, e);
        }
    }
}
