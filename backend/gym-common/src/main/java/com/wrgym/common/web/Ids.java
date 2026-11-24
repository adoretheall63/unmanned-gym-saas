package com.wrgym.common.web;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.exception.BizException;

import java.util.List;

/**
 * 业务 ID 解析（docs/接口文档.md §1.1：ID 为 1..9223372036854775807 的十进制字符串）。
 *
 * <p>Path/Query 中的 ID 字符串必须严格校验格式与上界，不能直接 Long.parseLong 后接受负数或超界值。
 */
public final class Ids {

    private static final String PATTERN = "^[1-9][0-9]{0,18}$";

    private Ids() {
    }

    public static long parse(String raw, String fieldName) {
        if (raw == null || !raw.matches(PATTERN)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "ID 格式不合法",
                    List.of(new FieldErrorItem(fieldName, "必须是 1..9223372036854775807 的十进制字符串")));
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 1) {
                throw new NumberFormatException(raw);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "ID 超出范围",
                    List.of(new FieldErrorItem(fieldName, "必须是 1..9223372036854775807 的十进制字符串")));
        }
    }
}
