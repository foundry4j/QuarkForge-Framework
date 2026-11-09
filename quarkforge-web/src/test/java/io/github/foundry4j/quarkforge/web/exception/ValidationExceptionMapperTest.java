package io.github.foundry4j.quarkforge.web.exception;

import io.github.foundry4j.quarkforge.core.api.Result;
import io.github.foundry4j.quarkforge.validation.annotation.Phone;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 校验文案的本地化：注解 message 写成键后，映射器按请求语言解析 `ValidationMessages`。
class ValidationExceptionMapperTest {

    /// 用于触发一次真实校验失败。
    public static class Request {

        @Phone
        public String phone;
    }

    @Test
    void localizesValidationTextByRequestLocale() {
        Set<ConstraintViolation<Request>> violations = violations();
        assertEquals(1, violations.size());

        assertTrue(message(violations, Locale.ENGLISH).contains("Invalid mobile number"));
        assertTrue(message(violations, Locale.SIMPLIFIED_CHINESE).contains("手机号格式不正确"));
    }

    private Set<ConstraintViolation<Request>> violations() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return validator.validateValue(Request.class, "phone", "123");
    }

    private String message(Set<ConstraintViolation<Request>> violations, Locale locale) {
        ValidationExceptionMapper mapper = new ValidationExceptionMapper() {
            @Override
            protected Locale requestLocale() {
                return locale;
            }
        };
        Result<?> result = (Result<?>) mapper.toResponse(new ConstraintViolationException(violations)).getEntity();
        return result.message();
    }
}
