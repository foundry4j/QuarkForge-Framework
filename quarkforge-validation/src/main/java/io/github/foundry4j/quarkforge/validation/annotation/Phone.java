package io.github.foundry4j.quarkforge.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import io.github.foundry4j.quarkforge.validation.validator.PhoneValidator;

/// 中国大陆手机号校验：`1[3-9]` 开头的 11 位数字。
///
/// `null` 视为合法（是否必填交给 `@NotNull`），空串视为非法。
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Phone {

    String message() default "{quarkforge.validation.phone}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
