package io.github.foundry4j.quarkforge.outbox.admin;

import io.github.foundry4j.quarkforge.outbox.OutboxMessage;

import java.time.Instant;

/// ## OutboxMessageView
/// 管理端点返回的发件箱消息视图，用于排查失败原因。
///
/// @param id            数据库主键，重放接口使用
/// @param messageId     消息业务 ID
/// @param destination   目标地址
/// @param payload       消息负载
/// @param headers       消息头 JSON
/// @param status        当前状态
/// @param attempts      已尝试次数
/// @param nextAttemptAt 下次可投递时间
/// @param lastError     最近一次失败原因
/// @param createdAt     创建时间
/// @param sentAt        投递成功时间
public record OutboxMessageView(Long id, String messageId, String destination, String payload, String headers,
                                String status, int attempts, Instant nextAttemptAt, String lastError,
                                Instant createdAt, Instant sentAt) {

    public static OutboxMessageView of(OutboxMessage message) {
        return new OutboxMessageView(message.id, message.messageId, message.destination, message.payload,
                message.headers, message.status.name(), message.attempts, message.nextAttemptAt,
                message.lastError, message.createdAt, message.sentAt);
    }
}
