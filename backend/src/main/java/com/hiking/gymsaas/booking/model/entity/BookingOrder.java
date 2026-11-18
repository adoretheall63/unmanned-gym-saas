package com.hiking.gymsaas.booking.model.entity;

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

/** 预约订单；对应 booking_order，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "booking_order", autoResultMap = true)
public class BookingOrder {

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
    @TableField(value = "slot_id")
    private Long slotId;

    @TableField(value = "order_no")
    private String orderNo;

    @TableField(value = "benefit_type")
    private String benefitType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "member_card_id")
    private Long memberCardId;

    @TableField(value = "start_at")
    private LocalDateTime startAt;

    @TableField(value = "end_at")
    private LocalDateTime endAt;

    /** 严格早于截止才能确认。 */
    @TableField(value = "pay_deadline")
    private LocalDateTime payDeadline;

    @TableField(value = "base_amount_cent")
    private Long baseAmountCent;

    @TableField(value = "overtime_price_cent")
    private Long overtimePriceCent;

    @TableField(value = "overtime_cap_cent")
    private Long overtimeCapCent;

    @TableField(value = "rule_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode ruleSnapshot;

    @TableField(value = "status")
    private String status;

    @TableField(value = "capacity_held")
    private Boolean capacityHeld;

    @TableField(value = "confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField(value = "cancel_stage")
    private String cancelStage;

    @TableField(value = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @TableField(value = "cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField(value = "cancel_reason")
    private String cancelReason;

    @TableField(value = "no_show_check_at")
    private LocalDateTime noShowCheckAt;

    @TableField(value = "review_required")
    private Boolean reviewRequired;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
