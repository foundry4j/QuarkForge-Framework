package io.github.foundry4j.quarkforge.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import io.github.foundry4j.quarkforge.validation.validator.BankCardValidator;

/// 银行卡号校验：12~19 位数字 + Luhn 校验算法。
///
/// `null` 视为合法（是否必填交给 `@NotNull`），空串视为非法。
@Documented
@Constraint(validatedBy = BankCardValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BankCard {

    String message() default "{quarkforge.validation.bank-card}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
