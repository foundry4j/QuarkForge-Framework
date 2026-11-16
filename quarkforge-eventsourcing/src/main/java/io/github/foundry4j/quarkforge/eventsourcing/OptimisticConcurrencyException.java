package io.github.foundry4j.quarkforge.eventsourcing;

/// ## OptimisticConcurrencyException
/// 追加事件时发现聚合已被并发修改（期望版本与已落库版本不一致）。
public class OptimisticConcurrencyException extends RuntimeException {

    public OptimisticConcurrencyException(String message) {
        super(message);
    }

    public OptimisticConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
