package io.github.foundry4j.quarkforge.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import io.github.foundry4j.quarkforge.validation.validator.IdCardValidator;

/// 中国大陆 18 位身份证校验：格式 + 出生日期合法性 + 加权校验位。
///
/// `null` 视为合法（是否必填交给 `@NotNull`），空串视为非法。
@Documented
@Constraint(validatedBy = IdCardValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IdCard {

    String message() default "{quarkforge.validation.id-card}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
