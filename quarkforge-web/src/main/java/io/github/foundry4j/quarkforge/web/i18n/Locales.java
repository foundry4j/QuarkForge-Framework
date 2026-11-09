package io.github.foundry4j.quarkforge.web.i18n;

import io.github.foundry4j.quarkforge.core.i18n.I18n;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.Locale;

/// ## Locales
/// 从 HTTP 请求头解析客户端语言。
///
/// 只做"挑选"，不做"翻译"：按 `Accept-Language` 的权重顺序，
/// 返回第一个文案源支持的语言；都不支持时回退 [I18n#defaultLocale()]。
public final class Locales {

    private Locales() {
    }

    /// 解析请求语言，`headers` 为空（如非 HTTP 场景）时返回默认语言。
    public static Locale resolve(HttpHeaders headers) {
        if (headers == null) {
            return I18n.defaultLocale();
        }
        try {
            return I18n.selectLocale(headers.getAcceptableLanguages());
        } catch (RuntimeException e) {
            // 请求头格式非法时不影响业务，退回默认语言
            return I18n.defaultLocale();
        }
    }
}
