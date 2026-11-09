package io.github.foundry4j.quarkforge.web.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.api.Result;
import io.github.foundry4j.quarkforge.core.i18n.I18n;
import io.github.foundry4j.quarkforge.web.i18n.Locales;
import io.quarkus.logging.Log;

/// ## GlobalExceptionMapper
/// 兜底异常处理：
///
/// - [WebApplicationException]（含 404/405/401 等框架内建异常）按状态码包装为统一响应体
/// - 其余未捕获异常记录日志并返回 500，避免堆栈信息泄露给调用方
///
/// 文案同样按 `Accept-Language` 本地化，查找键为 `error.<code>`。
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            return Response.status(status)
                    .entity(Result.error(status * 100, resolveMessage(wae, status)))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Log.error("未捕获的服务端异常", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Result.error(CommonErrorCode.INTERNAL_ERROR.code(),
                        I18n.message(CommonErrorCode.INTERNAL_ERROR, Locales.resolve(headers))))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String resolveMessage(WebApplicationException exception, int status) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        CommonErrorCode mapped = switch (status) {
            case 401 -> CommonErrorCode.UNAUTHORIZED;
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
            default -> CommonErrorCode.INTERNAL_ERROR;
        };
        return I18n.message(mapped, Locales.resolve(headers));
    }
}
