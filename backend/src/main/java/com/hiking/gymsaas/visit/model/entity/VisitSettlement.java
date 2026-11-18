package com.hiking.gymsaas.visit.model.entity;

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

/** 到店结算当前投影；对应 visit_settlement，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "visit_settlement", autoResultMap = true)
public class VisitSettlement {

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
    @TableField(value = "visit_id")
    private Long visitId;

    @TableField(value = "version")
    private Long version;

    @TableField(value = "status")
    private String status;

    @TableField(value = "overtime_minutes")
    private Integer overtimeMinutes;

    @TableField(value = "overtime_amount_cent")
    private Long overtimeAmountCent;

    @TableField(value = "adjustment_cent")
    private Long adjustmentCent;

    @TableField(value = "paid_cent")
    private Long paidCent;

    @TableField(value = "calculation_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode calculationSnapshot;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
