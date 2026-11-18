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

/** 设备命令与恢复任务；对应 device_operation，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "device_operation", autoResultMap = true)
public class DeviceOperation {

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

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "user_id")
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "order_id")
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "visit_id")
    private Long visitId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "credential_id")
    private Long credentialId;

    @TableField(value = "operation_no")
    private String operationNo;

    @TableField(value = "action")
    private String action;

    /** 凭证或灯控目标版本。 */
    @TableField(value = "generation")
    private Long generation;

    @TableField(value = "business_key")
    private String businessKey;

    @TableField(value = "status")
    private String status;

    @TableField(value = "accepted_at")
    private LocalDateTime acceptedAt;

    @TableField(value = "executed_at")
    private LocalDateTime executedAt;

    @TableField(value = "received_at")
    private LocalDateTime receivedAt;

    /** 每次领取递增的fencing版本，旧工作者写回必须匹配。 */
    @TableField(value = "attempts")
    private Long attempts;

    @TableField(value = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @TableField(value = "lease_until")
    private LocalDateTime leaseUntil;

    @TableField(value = "provider_command_id")
    private String providerCommandId;

    @TableField(value = "request_summary", typeHandler = JacksonTypeHandler.class)
    private JsonNode requestSummary;

    @TableField(value = "result_summary", typeHandler = JacksonTypeHandler.class)
    private JsonNode resultSummary;

    @TableField(value = "error_code")
    private String errorCode;

    @TableField(value = "error_message")
    private String errorMessage;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "entry_visit_guard")
    private Long entryVisitGuard;

    // 数据库生成列，仅映射查询结果，禁止随实体写入。
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "open_order_guard", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long openOrderGuard;

    // 数据库生成列，仅映射查询结果，禁止随实体写入。
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "running_device_guard", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long runningDeviceGuard;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
