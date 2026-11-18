package com.hiking.gymsaas.card.model.entity;

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

/** 用户持有卡与购买单；对应 member_card，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "member_card", autoResultMap = true)
public class MemberCard {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "user_id")
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "product_id")
    private Long productId;

    @TableField(value = "purchase_no")
    private String purchaseNo;

    @TableField(value = "card_type")
    private String cardType;

    @TableField(value = "product_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode productSnapshot;

    @TableField(value = "purchase_amount_cent")
    private Long purchaseAmountCent;

    @TableField(value = "total_count")
    private Integer totalCount;

    @TableField(value = "remaining_count")
    private Integer remainingCount;

    @TableField(value = "valid_from")
    private LocalDateTime validFrom;

    @TableField(value = "valid_to")
    private LocalDateTime validTo;

    @TableField(value = "status")
    private String status;

    @TableField(value = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @TableField(value = "freeze_reason")
    private String freezeReason;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
