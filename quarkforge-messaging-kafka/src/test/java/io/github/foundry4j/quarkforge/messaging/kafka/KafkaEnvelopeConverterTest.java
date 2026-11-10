package io.github.foundry4j.quarkforge.messaging.kafka;

import io.github.foundry4j.quarkforge.messaging.MessageEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// ## KafkaEnvelopeConverterTest
/// 验证 Kafka 记录 → 消息信封的还原转换（发布侧 toRecord 的逆操作）。
class KafkaEnvelopeConverterTest {

    @Test
    void convertsFullRecord() {
        Instant occurredAt = Instant.parse("2026-11-06T08:00:00Z");
        Headers headers = new RecordHeaders().add("tenant", "t1".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.created", 0, 7L,
                occurredAt.toEpochMilli(), TimestampType.CREATE_TIME, 0, 0,
                "m-1", "{\"orderNo\":\"A001\"}", headers, Optional.empty());

        MessageEnvelope envelope = KafkaMessages.toEnvelope("order.created", record);

        assertEquals("m-1", envelope.messageId());
        assertEquals("order.created", envelope.destination());
        assertEquals("{\"orderNo\":\"A001\"}", envelope.payload());
        assertEquals("t1", envelope.headers().get("tenant"));
        assertEquals(occurredAt, envelope.occurredAt());
    }

    @Test
    void fillsDefaultsWhenRecordBare() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.created", 0, 0L, null, null);

        MessageEnvelope envelope = KafkaMessages.toEnvelope("order.created", record);

        assertNotNull(envelope.messageId());
        assertEquals("order.created", envelope.destination());
        assertEquals("", envelope.payload());
        assertEquals(Map.of(), envelope.headers());
        assertNotNull(envelope.occurredAt());
    }

    @Test
    void roundTripKeepsIdentity() {
        MessageEnvelope original = MessageEnvelope.of("order.created", "{\"a\":1}", Map.of("k", "v"));
        ProducerRecord<String, String> record = KafkaMessages.toRecord(original);

        ConsumerRecord<String, String> consumed = new ConsumerRecord<>(record.topic(), 0, 9L,
                record.timestamp(), TimestampType.CREATE_TIME, 0, 0,
                record.key(), record.value(), record.headers(), Optional.empty());
        MessageEnvelope restored = KafkaMessages.toEnvelope(record.topic(), consumed);

        assertEquals(original.messageId(), restored.messageId());
        assertEquals(original.payload(), restored.payload());
        assertEquals(original.headers(), restored.headers());
        // Kafka 时间戳为毫秒精度，比较时截断到毫秒
        assertEquals(original.occurredAt().toEpochMilli(), restored.occurredAt().toEpochMilli());
    }

    @Test
    void restoresMessageIdFromHeaderWhenKeyBlank() {
        MessageEnvelope original = MessageEnvelope.of("order.created", "{}");
        ProducerRecord<String, String> record = KafkaMessages.toRecord(original);

        ConsumerRecord<String, String> consumed = new ConsumerRecord<>(record.topic(), 0, 1L,
                record.timestamp(), TimestampType.CREATE_TIME, 0, 0,
                "", record.value(), record.headers(), Optional.empty());
        MessageEnvelope restored = KafkaMessages.toEnvelope(record.topic(), consumed);

        assertEquals(original.messageId(), restored.messageId());
    }
}
