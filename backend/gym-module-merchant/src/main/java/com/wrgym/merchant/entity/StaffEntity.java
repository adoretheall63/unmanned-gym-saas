package com.wrgym.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wrgym.common.entity.BaseEntity;

import java.util.List;

/**
 * 后台员工（docs/数据库设计.md §1.3 admin_user）。
 *
 * <p>写入所有者：本模块（员工管理：创建/角色权限/状态/重置密码）。
 * gym-module-auth 只负责登录、退出与本人改密；两处都不得输出 password_hash。
 */
@TableName(value = "admin_user", autoResultMap = true)
public class StaffEntity extends BaseEntity {

    private Long merchantId;
    private String username;
    private String passwordHash;
    private String role;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> permissions;

    private Integer tokenVersion;
    private String status;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
