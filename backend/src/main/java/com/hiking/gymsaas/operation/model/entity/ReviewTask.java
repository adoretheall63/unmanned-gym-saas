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

/** 异常待办和复核；对应 review_task，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "review_task", autoResultMap = true)
public class ReviewTask {

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
    @TableField(value = "user_id")
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "order_id")
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "visit_id")
    private Long visitId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "member_card_id")
    private Long memberCardId;

    @TableField(value = "category")
    private String category;

    @TableField(value = "business_key")
    private String businessKey;

    @TableField(value = "status")
    private String status;

    @TableField(value = "evidence", typeHandler = JacksonTypeHandler.class)
    private JsonNode evidence;

    @TableField(value = "resolution")
    private String resolution;

    @TableField(value = "resolved_by")
    private Long resolvedBy;

    @TableField(value = "resolved_at")
    private LocalDateTime resolvedAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
