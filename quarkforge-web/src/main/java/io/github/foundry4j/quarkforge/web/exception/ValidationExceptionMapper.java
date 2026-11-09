package io.github.foundry4j.quarkforge.web.exception;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.Result;
import io.github.foundry4j.quarkforge.core.i18n.I18n;
import io.github.foundry4j.quarkforge.core.i18n.ResourceBundleMessageSource;
import io.github.foundry4j.quarkforge.web.i18n.Locales;

/// ## ValidationExceptionMapper
/// 将 Hibernate Validator 的参数校验失败（`@Valid` / `@NotNull` 等）
/// 转换为 400 + 统一响应体，message 汇总所有字段错误。
///
/// 文案本地化：注解的 `message` 写成 `{键}` 形式（框架自带注解默认如此），
/// 这里按请求的 `Accept-Language` 从 `i18n/messages*.properties` 解析；
/// 标准 `jakarta.validation.constraints.*` 的键交给 Hibernate Validator 自己的
/// 插值结果，保持原生行为。
@Provider
@Priority(Priorities.USER)
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    /// 校验注解文案库：base name 固定为 `ValidationMessages`，与 Bean Validation 规范一致，
    /// 这样同一份 properties 既服务 Hibernate Validator 自身插值，也服务本映射器的按请求语言解析。
    private static final ResourceBundleMessageSource VALIDATION_MESSAGES =
            new ResourceBundleMessageSource("ValidationMessages");

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Locale locale = requestLocale();
        String message = exception.getConstraintViolations().stream()
                .map(violation -> format(violation, locale))
                .sorted()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = I18n.message(CommonErrorCode.PARAM_ERROR, locale);
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Result.error(CommonErrorCode.PARAM_ERROR, message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /// 当前请求语言。抽成可覆写方法，便于脱离 HTTP 上下文单测。
    protected Locale requestLocale() {
        return Locales.resolve(headers);
    }

    private String format(ConstraintViolation<?> violation, Locale locale) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        String field = dot >= 0 ? path.substring(dot + 1) : path;
        return field + " " + resolveText(violation, locale);
    }

    private String resolveText(ConstraintViolation<?> violation, Locale locale) {
        String template = violation.getMessageTemplate();
        if (template != null && template.length() > 2 && template.startsWith("{") && template.endsWith("}")) {
            String key = template.substring(1, template.length() - 1);
            String resolved = VALIDATION_MESSAGES.resolve(key, locale);
            if (resolved != null) {
                return interpolate(resolved, violation.getConstraintDescriptor().getAttributes());
            }
        }
        // 未在框架文案库中登记（如标准注解），使用校验器已插值好的文案
        return violation.getMessage();
    }

    /// 用注解属性填充文案中的 `{min}`、`{max}` 等占位符。
    private String interpolate(String text, Map<String, Object> attributes) {
        String result = text;
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            result = result.replace("{" + attribute.getKey() + "}", String.valueOf(attribute.getValue()));
        }
        return result;
    }
}
