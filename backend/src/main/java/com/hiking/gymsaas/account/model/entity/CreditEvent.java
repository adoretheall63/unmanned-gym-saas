package com.hiking.gymsaas.account.model.entity;

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

/** 爽约信用和冲正；对应 credit_event，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "credit_event", autoResultMap = true)
public class CreditEvent {

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
    @TableField(value = "order_id")
    private Long orderId;

    @TableField(value = "business_key")
    private String businessKey;

    @TableField(value = "score_delta")
    private Integer scoreDelta;

    /** 原信用流水。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "reversal_of_id")
    private Long reversalOfId;

    @TableField(value = "reason")
    private String reason;

    @TableField(value = "occurred_at")
    private LocalDateTime occurredAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
