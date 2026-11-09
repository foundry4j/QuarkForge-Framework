package io.github.foundry4j.quarkforge.core.i18n;

import java.util.Locale;

/// ## MessageSource
/// 国际化文案来源 SPI。默认实现为 [ResourceBundleMessageSource]（读取
/// classpath 下的 `i18n/messages*.properties`），业务方可通过
/// [I18n#setMessageSource(MessageSource)] 换成数据库、配置中心等实现。
public interface MessageSource {

    /// 按键解析文案，缺失时返回 `null`（调用方自行回退到默认文案）。
    ///
    /// @param key    文案键，错误码统一使用 `error.<code>` 形式
    /// @param locale 目标语言，为 `null` 时使用默认语言
    /// @param args   占位参数，对应文案中的 `{0}`、`{1}`
    String resolve(String key, Locale locale, Object... args);

    /// 当前文案源是否支持该语言。用于从 `Accept-Language` 中挑选可用语言。
    default boolean supports(Locale locale) {
        return true;
    }
}
