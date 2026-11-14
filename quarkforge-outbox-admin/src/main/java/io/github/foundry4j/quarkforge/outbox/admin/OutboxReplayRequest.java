package io.github.foundry4j.quarkforge.outbox.admin;

import java.time.Instant;

/// ## OutboxReplayRequest
/// 批量重放条件，只作用于 `FAILED` 状态的消息：
///
/// @param destination 目标地址，为空表示不限
/// @param before      只重放创建时间不晚于该时刻的消息，为空表示不限
/// @param limit       本次最多重放条数，默认 200，上限 1000
public record OutboxReplayRequest(String destination, Instant before, Integer limit) {
}
