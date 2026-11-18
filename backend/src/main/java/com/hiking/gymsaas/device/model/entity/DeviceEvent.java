package com.hiking.gymsaas.device.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 设备事件收件箱；对应 device_event，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "device_event", autoResultMap = true)
public class DeviceEvent {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "store_id")
    private Long storeId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "device_id")
    private Long deviceId;

    @TableField(value = "dedup_key")
    private String dedupKey;

    @TableField(value = "event_type")
    private String eventType;

    @TableField(value = "occurred_at")
    private LocalDateTime occurredAt;

    @TableField(value = "received_at")
    private LocalDateTime receivedAt;

    @TableField(value = "identity_reliable")
    private Boolean identityReliable;

    @TableField(value = "payload_summary", typeHandler = JacksonTypeHandler.class)
    private JsonNode payloadSummary;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "matched_user_id")
    private Long matchedUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "matched_order_id")
    private Long matchedOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "operation_id")
    private Long operationId;

    @TableField(value = "processing_status")
    private String processingStatus;

    /** 每次领取递增的fencing版本，旧工作者写回必须匹配。 */
    @TableField(value = "attempts")
    private Long attempts;

    @TableField(value = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @TableField(value = "lease_until")
    private LocalDateTime leaseUntil;

    @TableField(value = "processed_at")
    private LocalDateTime processedAt;

    @TableField(value = "error_message")
    private String errorMessage;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
