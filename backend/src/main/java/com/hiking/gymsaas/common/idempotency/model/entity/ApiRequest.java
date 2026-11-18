package com.hiking.gymsaas.common.idempotency.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 持久化写请求幂等；对应 api_request，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "api_request", autoResultMap = true)
public class ApiRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "principal_type")
    private String principalType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "principal_id")
    private Long principalId;

    @TableField(value = "operation")
    private String operation;

    @TableField(value = "idempotency_key")
    private String idempotencyKey;

    @TableField(value = "request_hash")
    private String requestHash;

    @TableField(value = "resource_type")
    private String resourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "resource_id")
    private Long resourceId;

    @TableField(value = "http_status")
    private Integer httpStatus;

    @JsonIgnore
    @TableField(value = "result_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode resultSnapshot;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
