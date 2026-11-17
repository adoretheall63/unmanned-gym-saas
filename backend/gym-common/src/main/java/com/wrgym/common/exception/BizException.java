package com.wrgym.common.exception;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;

import java.util.List;

/**
 * 可预期的业务失败。由 {@link GlobalExceptionHandler} 转换为统一失败响应。
 *
 * <p>业务代码不抛裸 RuntimeException 表达可预期失败；未知异常统一映射 INTERNAL_ERROR。
 */
public class BizException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldErrorItem> fieldErrors;

    public BizException(ErrorCode code) {
        this(code, code.message(), null, null);
    }

    public BizException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public BizException(ErrorCode code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public BizException(ErrorCode code, String message, List<FieldErrorItem> fieldErrors) {
        this(code, message, fieldErrors, null);
    }

    public BizException(ErrorCode code, List<FieldErrorItem> fieldErrors) {
        this(code, code.message(), fieldErrors, null);
    }

    public BizException(ErrorCode code, String message, List<FieldErrorItem> fieldErrors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorItem> fieldErrors() {
        return fieldErrors;
    }
}
