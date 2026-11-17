package com.wrgym.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用实体基类（docs/数据库设计.md 通用约定）。
 *
 * <p>主键 {@code BIGINT AUTO_INCREMENT}（MyBatis-Plus {@link IdType#AUTO}），对外 JSON 序列化为字符串。
 * <p>{@code created_at} / {@code updated_at} 为 UTC DATETIME(3)，由 {@code GymMetaObjectHandler} 自动填充；
 * 只追加表（settlement_revision / card_ledger / credit_event / business_event）没有 updated_at，
 * 其实体可不继承本类或保留字段为 null（默认 NOT_NULL 策略不会写空列）。
 */
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
