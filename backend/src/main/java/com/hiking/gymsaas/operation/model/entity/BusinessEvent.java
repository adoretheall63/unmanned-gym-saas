package com.hiking.gymsaas.operation.model.entity;

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

/** 业务时间线与审计；对应 business_event，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "business_event", autoResultMap = true)
public class BusinessEvent {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @TableField(value = "event_type")
    private String eventType;

    @TableField(value = "target_type")
    private String targetType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "target_id")
    private Long targetId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "user_id")
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "admin_id")
    private Long adminId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "device_event_id")
    private Long deviceEventId;

    @TableField(value = "actor_type")
    private String actorType;

    @TableField(value = "request_id")
    private String requestId;

    @TableField(value = "reason")
    private String reason;

    @TableField(value = "detail", typeHandler = JacksonTypeHandler.class)
    private JsonNode detail;

    @TableField(value = "occurred_at")
    private LocalDateTime occurredAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
