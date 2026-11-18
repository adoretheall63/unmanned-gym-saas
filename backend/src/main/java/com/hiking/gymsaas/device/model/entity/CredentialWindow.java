package com.hiking.gymsaas.device.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 凭证版本与有效区间；对应 credential_window，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "credential_window", autoResultMap = true)
public class CredentialWindow {

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
    @TableField(value = "credential_id")
    private Long credentialId;

    /** 目标版本。 */
    @TableField(value = "generation")
    private Long generation;

    @TableField(value = "valid_from")
    private LocalDateTime validFrom;

    @TableField(value = "valid_to")
    private LocalDateTime validTo;

    /** 依据订单ID字符串数组，解释快照，不代替订单事实。 */
    @TableField(value = "basis_order_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> basisOrderIds;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
