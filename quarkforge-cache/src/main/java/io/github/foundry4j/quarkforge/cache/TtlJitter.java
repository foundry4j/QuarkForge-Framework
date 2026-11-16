package io.github.foundry4j.quarkforge.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/// ## TtlJitter
/// 为缓存 TTL 增加随机抖动，避免同一时刻批量写入的键同时过期（缓存雪崩）。
///
/// 只在基准 TTL 之上**向上**抖动 `[0, ratio]`，保证实际过期时间不早于配置值：
///
/// ```text
/// 实际 TTL = base × (1 + random(0, ratio))
/// ```
///
/// 例如 `default-ttl=PT5M`、`ttl-jitter=0.1`，实际 TTL 落在 5~5.5 分钟之间。
/// `ratio <= 0` 时保持原值。
public final class TtlJitter {

    private TtlJitter() {
    }

    /// 计算抖动后的 TTL。
    ///
    /// @param ttl   基准 TTL
    /// @param ratio 抖动比例，建议 0.05~0.2，超过 1 按 1 处理
    /// @return 抖动后的 TTL
    public static Duration apply(Duration ttl, double ratio) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ratio <= 0) {
            return ttl;
        }
        double bounded = Math.min(ratio, 1.0);
        long base = ttl.toNanos();
        long extra = (long) (base * ThreadLocalRandom.current().nextDouble(bounded));
        return Duration.ofNanos(base + extra);
    }
}
