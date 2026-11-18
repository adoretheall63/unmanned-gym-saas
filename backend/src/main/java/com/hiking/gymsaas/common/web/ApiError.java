package com.hiking.gymsaas.common.web;

import com.hiking.gymsaas.common.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record ApiError(String code, String message, String requestId,
                       OffsetDateTime serverTime, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) { }

    public static ApiError of(ErrorCode code) {
        return of(code, List.of());
    }

    public static ApiError of(ErrorCode code, List<FieldError> fields) {
        return new ApiError(code.name(), code.message(), RequestIds.current(),
                OffsetDateTime.now(ZoneOffset.UTC), List.copyOf(fields));
    }
}
