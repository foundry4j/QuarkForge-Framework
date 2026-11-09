package io.github.foundry4j.quarkforge.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import io.github.foundry4j.quarkforge.validation.validator.EnumValidValidator;

/// 枚举值校验：字符串字段必须是目标枚举的某个常量名。
/// 避免「字符串字段存了枚举集合之外的值」这类脏数据。
///
/// ```java
/// @EnumValid(OrderStatus.class)
/// private String status;
/// ```
///
/// `null` 视为合法（是否必填交给 `@NotNull`），空串视为非法。
@Documented
@Constraint(validatedBy = EnumValidValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValid {

    /// 目标枚举类型。
    Class<? extends Enum<?>> value();

    /// 是否忽略大小写比较。
    boolean ignoreCase() default false;

    String message() default "{quarkforge.validation.enum-valid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
