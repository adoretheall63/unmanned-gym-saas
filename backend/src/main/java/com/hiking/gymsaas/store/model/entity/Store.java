package com.hiking.gymsaas.store.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 门店与运营规则；对应 store，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "store", autoResultMap = true)
public class Store {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @TableField(value = "name")
    private String name;

    @TableField(value = "address")
    private String address;

    @TableField(value = "latitude")
    private BigDecimal latitude;

    @TableField(value = "longitude")
    private BigDecimal longitude;

    @TableField(value = "description")
    private String description;

    @TableField(value = "images", typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    @TableField(value = "open_time")
    private LocalTime openTime;

    @TableField(value = "close_time")
    private LocalTime closeTime;

    @TableField(value = "is_24h")
    private Boolean is24h;

    @TableField(value = "ends_next_day")
    private Boolean endsNextDay;

    @TableField(value = "capacity")
    private Integer capacity;

    @TableField(value = "slot_price_cent")
    private Long slotPriceCent;

    @TableField(value = "overtime_price_cent")
    private Long overtimePriceCent;

    @TableField(value = "overtime_cap_cent")
    private Long overtimeCapCent;

    @TableField(value = "status")
    private String status;

    @TableField(value = "admission_paused")
    private Boolean admissionPaused;

    @TableField(value = "pause_reason")
    private String pauseReason;

    @TableField(value = "light_mode")
    private String lightMode;

    /** 目标版本，新人或告警使旧关灯任务失效。 */
    @TableField(value = "light_generation")
    private Long lightGeneration;

    @TableField(value = "message_health")
    private String messageHealth;

    @TableField(value = "last_human_alarm_at")
    private LocalDateTime lastHumanAlarmAt;

    @TableField(value = "last_message_observed_at")
    private LocalDateTime lastMessageObservedAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
