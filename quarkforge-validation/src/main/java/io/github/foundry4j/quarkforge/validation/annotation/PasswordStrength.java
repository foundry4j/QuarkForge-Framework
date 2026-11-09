package io.github.foundry4j.quarkforge.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import io.github.foundry4j.quarkforge.validation.validator.PasswordStrengthValidator;

/// 密码强度校验，可配置长度区间与字符要求。
///
/// `null` 视为合法（是否必填交给 `@NotNull`），空串视为非法。
@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordStrength {

    String message() default "{quarkforge.validation.password-strength}";

    /// 最小长度。
    int min() default 8;

    /// 最大长度。
    int max() default 64;

    /// 是否必须包含数字。
    boolean requireDigit() default true;

    /// 是否必须包含字母。
    boolean requireLetter() default true;

    /// 是否必须包含特殊字符。
    boolean requireSpecial() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
