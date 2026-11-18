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

/** 结算不可变修订；对应 settlement_revision，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "settlement_revision", autoResultMap = true)
public class SettlementRevision {

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
    @TableField(value = "settlement_id")
    private Long settlementId;

    @TableField(value = "version")
    private Long version;

    @TableField(value = "total_due_cent")
    private Long totalDueCent;

    @TableField(value = "calculation_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode calculationSnapshot;

    @TableField(value = "reason")
    private String reason;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "admin_id")
    private Long adminId;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
