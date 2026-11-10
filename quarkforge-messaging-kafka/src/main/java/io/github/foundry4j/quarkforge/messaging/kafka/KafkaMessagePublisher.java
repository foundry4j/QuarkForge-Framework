package io.github.foundry4j.quarkforge.messaging.kafka;

import io.github.foundry4j.quarkforge.messaging.MessageEnvelope;
import io.github.foundry4j.quarkforge.messaging.MessagePublisher;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/// ## KafkaMessagePublisher
/// Kafka 消息发布实现（引入本模块后自动覆盖 `LocalMessagePublisher`）。
///
/// `destination` 即 Kafka topic；record key 为 `messageId`，同 key 消息按分区保序。
/// Kafka 的 topic 由 broker 自动创建或预先建好，发布侧不做声明。
///
/// 配置项：
///
/// ```properties
/// quarkforge.messaging.kafka.bootstrap-servers=localhost:9092
/// quarkforge.messaging.kafka.acks=1        # all 更可靠、吞吐略降
/// ```
@ApplicationScoped
public class KafkaMessagePublisher implements MessagePublisher {

    private static final Logger LOG = Logger.getLogger(KafkaMessagePublisher.class);

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "quarkforge.messaging.kafka.bootstrap-servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "quarkforge.messaging.kafka.acks", defaultValue = "1")
    String acks;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    void init() {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class.getName());
        config.put("value.serializer", StringSerializer.class.getName());
        config.put("acks", acks);
        config.put("client.id", "quarkforge-producer");
        producer = KafkaProducer.create(vertx, config);
        LOG.infof("Kafka 生产者已就绪 bootstrap=%s acks=%s", bootstrapServers, acks);
    }

    @Override
    public CompletionStage<Void> publish(MessageEnvelope message) {
        Objects.requireNonNull(message, "message");
        if (message.destination().isBlank()) {
            throw new IllegalArgumentException("消息 destination 不能为空");
        }
        ProducerRecord<String, String> record = KafkaMessages.toRecord(message);
        KafkaProducerRecord<String, String> vertxRecord = KafkaProducerRecord
                .create(record.topic(), record.key(), record.value(), record.timestamp(), null)
                .addHeaders(toVertxHeaders(record));
        return producer.send(vertxRecord).<Void>mapEmpty().toCompletionStage();
    }

    private List<KafkaHeader> toVertxHeaders(ProducerRecord<String, String> record) {
        List<KafkaHeader> headers = new ArrayList<>();
        record.headers().forEach(header -> headers.add(KafkaHeader.header(
                header.key(), header.value() == null ? Buffer.buffer() : Buffer.buffer(header.value()))));
        return headers;
    }

    @PreDestroy
    void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
