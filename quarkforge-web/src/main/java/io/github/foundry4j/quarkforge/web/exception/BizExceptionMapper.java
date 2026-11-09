package io.github.foundry4j.quarkforge.web.exception;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.foundry4j.quarkforge.core.api.Result;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.core.exception.RateLimitException;
import io.github.foundry4j.quarkforge.core.i18n.I18n;
import io.github.foundry4j.quarkforge.web.i18n.Locales;
import io.quarkus.logging.Log;

/// ## BizExceptionMapper
/// 将业务异常转换为统一 [Result] 响应，HTTP 状态码由错误码段位推导。
///
/// 文案按请求的 `Accept-Language` 本地化：异常未携带自定义文案时，
/// 用 `error.<code>` 查找；业务自己传了文案则原样返回。
@Provider
public class BizExceptionMapper implements ExceptionMapper<BizException> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(BizException exception) {
        int status = HttpStatuses.fromBizCode(exception.code());
        String message = resolveMessage(exception);
        if (!(exception instanceof RateLimitException)) {
            // 4xx 属于预期内的业务分支，只记录一行摘要；5xx 才输出堆栈便于排查
            if (status >= 500) {
                Log.warnf(exception, "业务异常 [%d] %s", exception.code(), message);
            } else {
                Log.warnf("业务异常 [%d] %s", exception.code(), message);
            }
        }
        return Response.status(status)
                .entity(Result.error(exception.code(), message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String resolveMessage(BizException exception) {
        if (exception.hasCustomMessage() || exception.errorCode() == null) {
            return exception.getMessage();
        }
        return I18n.message(exception.errorCode(), Locales.resolve(headers));
    }
}
