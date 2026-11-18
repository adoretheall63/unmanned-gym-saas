package com.hiking.gymsaas.payment.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 现金收退款流水；对应 payment_record，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "payment_record", autoResultMap = true)
public class PaymentRecord {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "payment_no")
    private String paymentNo;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "order_id")
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "visit_id")
    private Long visitId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "member_card_id")
    private Long memberCardId;

    /** 退款关联原成功收款。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "refund_of_id")
    private Long refundOfId;

    @TableField(value = "business_key")
    private String businessKey;

    @TableField(value = "kind")
    private String kind;

    @TableField(value = "channel")
    private String channel;

    @TableField(value = "amount_cent")
    private Long amountCent;

    @TableField(value = "status")
    private String status;

    @TableField(value = "completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "failure_reason")
    private String failureReason;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
