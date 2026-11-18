package com.hiking.gymsaas.visit.model.entity;

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

/** 一次实际到店；对应 visit_session，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "visit_session", autoResultMap = true)
public class VisitSession {

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

    @TableField(value = "visit_no")
    private String visitNo;

    @TableField(value = "entry_at")
    private LocalDateTime entryAt;

    @TableField(value = "exit_at")
    private LocalDateTime exitAt;

    @TableField(value = "entry_source")
    private String entrySource;

    @TableField(value = "exit_source")
    private String exitSource;

    @TableField(value = "authorized_end_at")
    private LocalDateTime authorizedEndAt;

    /** 末段订单，决定超时规则。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "last_order_id")
    private Long lastOrderId;

    @TableField(value = "status")
    private String status;

    @TableField(value = "review_required")
    private Boolean reviewRequired;

    // 数据库生成列，仅映射查询结果，禁止随实体写入。
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "active_user_guard", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeUserGuard;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
