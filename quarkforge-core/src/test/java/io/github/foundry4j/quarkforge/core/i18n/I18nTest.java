package io.github.foundry4j.quarkforge.core.i18n;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class I18nTest {

    @AfterEach
    void reset() {
        I18n.setMessageSource(null);
        I18n.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void resolvesChineseText() {
        assertEquals("请求的资源不存在", I18n.message(CommonErrorCode.NOT_FOUND, Locale.SIMPLIFIED_CHINESE));
    }

    @Test
    void resolvesEnglishText() {
        assertEquals("The requested resource does not exist", I18n.message(CommonErrorCode.NOT_FOUND, Locale.ENGLISH));
    }

    @Test
    void resolvesByKey() {
        assertEquals("success", I18n.message("error.200", Locale.ENGLISH));
        assertNull(I18n.message("error.99999", Locale.ENGLISH));
    }

    @Test
    void selectsFirstSupportedLanguage() {
        assertEquals(Locale.ENGLISH, I18n.selectLocale(List.of(Locale.FRENCH, Locale.ENGLISH)));
    }

    @Test
    void fallsBackToDefaultLocaleWhenNothingSupported() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, I18n.selectLocale(List.of(Locale.FRENCH)));
        assertEquals(Locale.SIMPLIFIED_CHINESE, I18n.selectLocale(List.of()));
    }

    @Test
    void fallsBackToErrorCodeMessageWhenKeyMissing() {
        ErrorCode custom = new ErrorCode() {
            @Override
            public int code() {
                return 49999;
            }

            @Override
            public String message() {
                return "库存不足";
            }
        };
        assertEquals("库存不足", I18n.message(custom, Locale.ENGLISH));
    }

    @Test
    void usesCustomMessageSource() {
        I18n.setMessageSource((key, locale, args) -> "K:" + key);
        assertEquals("K:error.40400", I18n.message(CommonErrorCode.NOT_FOUND, Locale.ENGLISH));
    }
}
