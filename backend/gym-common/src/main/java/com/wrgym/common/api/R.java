package com.wrgym.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wrgym.common.web.RequestContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 统一响应体（docs/接口文档.md §1.1 / §1.4）。
 *
 * <p>成功：code="OK"（或具体成功码）、message、requestId、serverTime、data。
 * <p>失败：data 为 null，额外返回非空 fieldErrors[]，HTTP 状态由 {@link ErrorCode#httpStatus()} 决定。
 * <p>HTTP 状态码不放在 body 里；Controller/异常处理器返回 ResponseEntity 时使用该枚举的状态。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class R<T> {

    /** 成功码。契约未定义成功码枚举，统一使用 OK。 */
    public static final String SUCCESS_CODE = "OK";

    private final String code;
    private final String message;
    private final String requestId;
    private final OffsetDateTime serverTime;
    private final T data;
    private final List<FieldErrorItem> fieldErrors;

    private R(String code, String message, String requestId, OffsetDateTime serverTime, T data,
              List<FieldErrorItem> fieldErrors) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.serverTime = serverTime;
        this.data = data;
        this.fieldErrors = fieldErrors;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "ok", RequestContext.currentRequestId(),
                OffsetDateTime.now(ZoneOffset.UTC), data, null);
    }

    public static R<Void> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode, message, null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message, List<FieldErrorItem> fieldErrors) {
        return new R<>(errorCode.code(), message, RequestContext.currentRequestId(),
                OffsetDateTime.now(ZoneOffset.UTC), null,
                fieldErrors == null || fieldErrors.isEmpty() ? List.of() : List.copyOf(fieldErrors));
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getServerTime() {
        return serverTime;
    }

    public T getData() {
        return data;
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }
}
