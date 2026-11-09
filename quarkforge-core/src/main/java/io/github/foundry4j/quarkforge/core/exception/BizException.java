package io.github.foundry4j.quarkforge.core.exception;

import io.github.foundry4j.quarkforge.core.api.ErrorCode;
import io.github.foundry4j.quarkforge.core.api.Result;

/// ## BizException
/// 业务异常基类。web 模块的全局异常处理器会将其转换为
/// [Result] 响应，HTTP 状态码由
/// [ErrorCode] 段位推导（4xxxx → 对应 4xx，5xxxx → 500）。
public class BizException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final int code;
    private final boolean customMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.code = errorCode.code();
        this.customMessage = false;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.code = errorCode.code();
        this.customMessage = true;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.code = errorCode.code();
        this.customMessage = true;
    }

    public BizException(int code, String message) {
        super(message);
        this.errorCode = null;
        this.code = code;
        this.customMessage = true;
    }

    /// 错误码数值。
    public int code() {
        return code;
    }

    /// 错误码对象，直接以数值构造时返回 `null`。
    public ErrorCode errorCode() {
        return errorCode;
    }

    /// 是否携带业务自定义文案。为 `false` 时由 web 层按请求语言本地化错误码文案。
    public boolean hasCustomMessage() {
        return customMessage;
    }
}
