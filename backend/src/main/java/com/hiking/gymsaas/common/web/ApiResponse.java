package com.hiking.gymsaas.common.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(String code, String message, String requestId,
                             OffsetDateTime serverTime, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "操作成功", RequestIds.current(),
                OffsetDateTime.now(ZoneOffset.UTC), data);
    }
}
