package io.github.foundry4j.quarkforge.messaging.kafka;

import io.github.foundry4j.quarkforge.messaging.MessageEnvelope;
import io.github.foundry4j.quarkforge.messaging.MessageSubscriber;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/// ## KafkaMessageSubscriber
/// Kafka 消费端，[MessageSubscriber] 的生产实现（覆盖本地默认实现）：
///
/// 1. 每个 topic 一个 `KafkaConsumer`，统一挂在 `group-id` 消费组下；
/// 2. 关闭自动提交：处理成功后手动 `commit`；
/// 3. 处理器抛异常时记录日志并**仍然提交**（跳过该条），与 RabbitMQ 的
///    nack 不入队策略一致，避免毒消息卡住整个分区；
///    需要重试/死信的业务请自行配置 DLQ 或在处理器内捕获重试；
/// 4. 应用重启后从消费组上次提交的位点继续。
///
/// 配置项：
///
/// | 配置 | 默认值 | 说明 |
/// |------|--------|------|
/// | `quarkforge.messaging.kafka.bootstrap-servers` | `localhost:9092` | broker 地址 |
/// | `quarkforge.messaging.kafka.group-id` | `quarkforge` | 消费组 |
/// | `quarkforge.messaging.kafka.auto-offset-reset` | `latest` | 无位点时的起始位置 |
@ApplicationScoped
public class KafkaMessageSubscriber implements MessageSubscriber {

    private static final Logger LOG = Logger.getLogger(KafkaMessageSubscriber.class);

    @ConfigProperty(name = "quarkforge.messaging.kafka.bootstrap-servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "quarkforge.messaging.kafka.group-id", defaultValue = "quarkforge")
    String groupId;

    @ConfigProperty(name = "quarkforge.messaging.kafka.auto-offset-reset", defaultValue = "latest")
    String autoOffsetReset;

    @Inject
    Vertx vertx;

    private final List<KafkaConsumer<String, String>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public CompletionStage<Void> subscribe(String topic, Consumer<MessageEnvelope> handler) {
        KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, consumerConfig());
        consumers.add(consumer);
        consumer.handler(record -> handle(topic, record, handler, consumer));
        return consumer.subscribe(topic).toCompletionStage();
    }

    private Map<String, String> consumerConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("group.id", groupId);
        config.put("key.deserializer", StringDeserializer.class.getName());
        config.put("value.deserializer", StringDeserializer.class.getName());
        config.put("enable.auto.commit", "false");
        config.put("auto.offset.reset", autoOffsetReset);
        return config;
    }

    private void handle(String topic, KafkaConsumerRecord<String, String> record,
                        Consumer<MessageEnvelope> handler, KafkaConsumer<String, String> consumer) {
        try {
            handler.accept(KafkaMessages.toEnvelope(topic, record.record()));
            consumer.commit();
        } catch (Exception e) {
            LOG.warnf(e, "Kafka 消息处理失败，跳过该条 topic=%s offset=%d", topic, record.offset());
            consumer.commit();
        }
    }

    @PreDestroy
    void close() {
        consumers.forEach(KafkaConsumer::close);
    }
}
