package com.hiking.gymsaas.device.model.entity;

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

/** 设备注册和观察状态；对应 device，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "device", autoResultMap = true)
public class Device {

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "merchant_id")
    private Long merchantId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "store_id")
    private Long storeId;

    @TableField(value = "name")
    private String name;

    @TableField(value = "device_type")
    private String deviceType;

    @TableField(value = "direction")
    private String direction;

    @TableField(value = "provider")
    private String provider;

    @JsonIgnore
    @TableField(value = "provider_account_ref")
    private String providerAccountRef;

    @JsonIgnore
    @TableField(value = "provider_device_id")
    private String providerDeviceId;

    @TableField(value = "model")
    private String model;

    @TableField(value = "channel_no")
    private Integer channelNo;

    @TableField(value = "capabilities", typeHandler = JacksonTypeHandler.class)
    private JsonNode capabilities;

    @TableField(value = "status")
    private String status;

    @TableField(value = "online_status")
    private String onlineStatus;

    @TableField(value = "battery_percent")
    private Integer batteryPercent;

    @TableField(value = "observed_at")
    private LocalDateTime observedAt;

    @TableField(value = "scan_code")
    private String scanCode;

    @TableField(value = "mock_scenario")
    private String mockScenario;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
