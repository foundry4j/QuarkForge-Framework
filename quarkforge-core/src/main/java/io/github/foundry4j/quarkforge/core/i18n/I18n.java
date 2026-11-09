package io.github.foundry4j.quarkforge.core.i18n;

import io.github.foundry4j.quarkforge.core.api.ErrorCode;

import java.util.List;
import java.util.Locale;

/// ## I18n
/// 国际化入口（纯 JDK 实现，`core` 不依赖任何框架）。
///
/// 约定：
///
/// - 错误码文案的键为 `error.<code>`，例如 `error.40400`
/// - 找不到对应文案时回退到 [ErrorCode#message()]，再找不到则返回键本身
/// - 语言选择：优先请求方给出的语言列表，其次 [I18n#defaultLocale()]
///
/// ```java
/// // 业务代码通常无需关心语言，交给 web 层异常处理器按 Accept-Language 解析
/// throw new BizException(CommonErrorCode.NOT_FOUND);
///
/// // 需要手动取文案时
/// String text = I18n.message("error.40400", Locale.SIMPLIFIED_CHINESE);
/// String text2 = I18n.message(CommonErrorCode.NOT_FOUND, Locale.ENGLISH);
/// ```
public final class I18n {

    /// 错误码文案前缀。
    public static final String ERROR_CODE_PREFIX = "error.";

    private static final String DEFAULT_BASE_NAME = "i18n/messages";

    private static volatile MessageSource source = new ResourceBundleMessageSource(DEFAULT_BASE_NAME);

    private static volatile Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    private I18n() {
    }

    /// 替换文案源，传入 `null` 时恢复默认的 properties 实现。
    public static void setMessageSource(MessageSource messageSource) {
        source = messageSource != null ? messageSource : new ResourceBundleMessageSource(DEFAULT_BASE_NAME);
    }

    /// 当前文案源。
    public static MessageSource messageSource() {
        return source;
    }

    /// 默认语言，未从请求中解析到语言时使用，默认 `zh_CN`。
    public static Locale defaultLocale() {
        return defaultLocale;
    }

    public static void setDefaultLocale(Locale locale) {
        if (locale != null) {
            defaultLocale = locale;
        }
    }

    /// 从候选语言中挑选文案源支持的第一个，都不支持时返回默认语言。
    public static Locale selectLocale(List<Locale> candidates) {
        if (candidates != null) {
            for (Locale candidate : candidates) {
                if (candidate == null || candidate.getLanguage().isEmpty() || "*".equals(candidate.getLanguage())) {
                    continue;
                }
                if (source.supports(candidate)) {
                    return candidate;
                }
            }
        }
        return defaultLocale;
    }

    /// 按错误码取当前默认语言的文案。
    public static String message(ErrorCode errorCode) {
        return message(errorCode, defaultLocale);
    }

    /// 按错误码取指定语言的文案，缺失时回退到错误码自带的默认文案。
    public static String message(ErrorCode errorCode, Locale locale) {
        if (errorCode == null) {
            return null;
        }
        String text = message(ERROR_CODE_PREFIX + errorCode.code(), locale);
        return text != null ? text : errorCode.message();
    }

    /// 按键取指定语言的文案，缺失时返回 `null`。
    public static String message(String key, Locale locale) {
        return source.resolve(key, locale != null ? locale : defaultLocale);
    }

    /// 按键取指定语言的文案并填充 `{0}`、`{1}` 占位参数。
    public static String message(String key, Locale locale, Object... args) {
        return source.resolve(key, locale != null ? locale : defaultLocale, args);
    }
}
