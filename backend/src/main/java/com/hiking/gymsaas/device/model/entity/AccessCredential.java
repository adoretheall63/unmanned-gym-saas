package com.hiking.gymsaas.device.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 共享通行凭证；对应 access_credential，不直接充当接口DTO。 */
@Getter
@Setter
@TableName(value = "access_credential", autoResultMap = true)
public class AccessCredential {

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
    @TableField(value = "device_id")
    private Long deviceId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField(value = "device_person_id")
    private Long devicePersonId;

    @TableField(value = "credential_type")
    private String credentialType;

    /** 当前目标版本。 */
    @TableField(value = "generation")
    private Long generation;

    /** 实际结果确认的版本。 */
    @TableField(value = "observed_generation")
    private Long observedGeneration;

    @TableField(value = "observed_status")
    private String observedStatus;

    @JsonIgnore
    @TableField(value = "provider_credential_id")
    private String providerCredentialId;

    @JsonIgnore
    @TableField(value = "secret_ciphertext")
    private String secretCiphertext;

    @TableField(value = "last_error")
    private String lastError;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    // 由数据库UTC会话生成，避免应用时间覆盖事实创建/更新时间。
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
