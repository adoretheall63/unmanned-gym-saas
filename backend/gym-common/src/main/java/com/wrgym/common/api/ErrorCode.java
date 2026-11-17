package com.wrgym.common.api;

import org.springframework.http.HttpStatus;

/**
 * 稳定业务错误码（docs/接口文档.md §1.4）。
 *
 * <p>前端只按 {@link #code()} 判断状态，不解析 message。HTTP 状态见 {@link #httpStatus()}。
 * <p>契约表格未列 500，{@link #INTERNAL_ERROR} 是实现补充：只用于未预期异常，响应不得暴露堆栈。
 */
public enum ErrorCode {

    // 400
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", HttpStatus.BAD_REQUEST),

    // 401 / 403
    UNAUTHENTICATED("UNAUTHENTICATED", "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "没有操作权限", HttpStatus.FORBIDDEN),
    VIDEO_STORAGE_FORBIDDEN("VIDEO_STORAGE_FORBIDDEN", "没有录像存储授权", HttpStatus.FORBIDDEN),

    // 404
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    RECORDING_NOT_FOUND("RECORDING_NOT_FOUND", "没有对应录像", HttpStatus.NOT_FOUND),

    // 409 状态与并发冲突
    SLOT_FULL("SLOT_FULL", "该时段已约满", HttpStatus.CONFLICT),
    SLOT_STARTED("SLOT_STARTED", "时段已开始", HttpStatus.CONFLICT),
    SLOT_COMMITTED("SLOT_COMMITTED", "时段已有售出承诺", HttpStatus.CONFLICT),
    TIME_OVERLAP("TIME_OVERLAP", "与本人已有预约时间重叠", HttpStatus.CONFLICT),
    PAYMENT_EXPIRED("PAYMENT_EXPIRED", "支付已超时", HttpStatus.CONFLICT),
    STATE_CONFLICT("STATE_CONFLICT", "当前状态不允许该操作", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "同一幂等键的请求内容不一致", HttpStatus.CONFLICT),
    VERSION_CONFLICT("VERSION_CONFLICT", "数据版本已变化", HttpStatus.CONFLICT),
    PREVIEW_STALE("PREVIEW_STALE", "规则预览已过期，请重新预览", HttpStatus.CONFLICT),
    RULE_CONFLICT("RULE_CONFLICT", "规则变更与既有承诺冲突", HttpStatus.CONFLICT),
    REVIEW_CHANGED("REVIEW_CHANGED", "待办已被他人处理", HttpStatus.CONFLICT),
    REVIEW_DEPENDENCY("REVIEW_DEPENDENCY", "仍有未完成依赖", HttpStatus.CONFLICT),
    DEVICE_IN_USE("DEVICE_IN_USE", "设备仍被使用", HttpStatus.CONFLICT),
    CARD_HAS_ACTIVE_ORDERS("CARD_HAS_ACTIVE_ORDERS", "卡仍有未解除的预约", HttpStatus.CONFLICT),
    ORDER_ALREADY_USED("ORDER_ALREADY_USED", "订单已被使用", HttpStatus.CONFLICT),
    OCCUPANCY_UNKNOWN("OCCUPANCY_UNKNOWN", "在场人数无法确认", HttpStatus.CONFLICT),
    OPERATION_UNCERTAIN("OPERATION_UNCERTAIN", "设备命令结果未知", HttpStatus.CONFLICT),
    LIGHT_OFF_UNSAFE("LIGHT_OFF_UNSAFE", "当前条件不允许关灯", HttpStatus.CONFLICT),
    OPEN_REQUIRED("OPEN_REQUIRED", "需要先有成功的开门记录", HttpStatus.CONFLICT),
    OPEN_RESULT_UNKNOWN("OPEN_RESULT_UNKNOWN", "开门结果未知", HttpStatus.CONFLICT),
    OPEN_ALREADY_CONSUMED("OPEN_ALREADY_CONSUMED", "开门记录已被使用", HttpStatus.CONFLICT),
    ADMISSION_PAUSED("ADMISSION_PAUSED", "门店已暂停新准入", HttpStatus.CONFLICT),
    ENTRY_WINDOW_CLOSED("ENTRY_WINDOW_CLOSED", "不在允许入场的时间窗口", HttpStatus.CONFLICT),
    OUTSTANDING_BALANCE("OUTSTANDING_BALANCE", "存在未结清欠费", HttpStatus.CONFLICT),
    CREDIT_RESTRICTED("CREDIT_RESTRICTED", "信用限制中", HttpStatus.CONFLICT),
    BLOCKED("BLOCKED", "已被商家拉黑", HttpStatus.CONFLICT),
    REVIEW_REQUIRED("REVIEW_REQUIRED", "需要人工复核", HttpStatus.CONFLICT),
    RETRY_UNSAFE("RETRY_UNSAFE", "结果不确定，不能直接重试", HttpStatus.CONFLICT),

    // 412 / 428 配置并发
    ETAG_MISMATCH("ETAG_MISMATCH", "If-Match 与当前配置不一致", HttpStatus.PRECONDITION_FAILED),
    PRECONDITION_REQUIRED("PRECONDITION_REQUIRED", "缺少 If-Match", HttpStatus.PRECONDITION_REQUIRED),

    // 413 / 415
    FILE_TOO_LARGE("FILE_TOO_LARGE", "上传文件过大", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "不支持的文件类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // 422 格式正确但业务不允许
    BENEFIT_NOT_SUPPORTED("BENEFIT_NOT_SUPPORTED", "当前不支持该权益类型", HttpStatus.UNPROCESSABLE_ENTITY),
    BENEFIT_MISMATCH("BENEFIT_MISMATCH", "权益与订单不匹配", HttpStatus.UNPROCESSABLE_ENTITY),
    CARD_EXPIRED("CARD_EXPIRED", "卡已过期", HttpStatus.UNPROCESSABLE_ENTITY),
    CARD_FROZEN("CARD_FROZEN", "卡已冻结", HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_COUNT("INSUFFICIENT_COUNT", "次数不足", HttpStatus.UNPROCESSABLE_ENTITY),
    PHONE_BINDING_UNAVAILABLE("PHONE_BINDING_UNAVAILABLE", "手机号绑定不可用", HttpStatus.UNPROCESSABLE_ENTITY),
    SECRET_UNAVAILABLE("SECRET_UNAVAILABLE", "当前无法查看该凭据", HttpStatus.UNPROCESSABLE_ENTITY),
    CAPABILITY_UNSUPPORTED("CAPABILITY_UNSUPPORTED", "设备不具备该能力", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_TIME_RANGE("INVALID_TIME_RANGE", "时间范围不合法", HttpStatus.UNPROCESSABLE_ENTITY),

    // 429 / 501 / 503
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    INTEGRATION_NOT_VERIFIED("INTEGRATION_NOT_VERIFIED", "厂商协议尚未联调验收", HttpStatus.NOT_IMPLEMENTED),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    VIDEO_AUTHORIZATION_UNAVAILABLE("VIDEO_AUTHORIZATION_UNAVAILABLE", "视频授权暂不可用", HttpStatus.SERVICE_UNAVAILABLE),

    // 开发入口
    DEV_DISABLED("DEV_DISABLED", "开发入口未开启", HttpStatus.NOT_FOUND),

    // 实现补充
    INTERNAL_ERROR("INTERNAL_ERROR", "服务内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
