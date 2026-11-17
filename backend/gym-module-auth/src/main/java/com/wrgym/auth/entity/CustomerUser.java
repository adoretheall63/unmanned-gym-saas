package com.wrgym.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wrgym.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * 全局顾客（docs/数据库设计.md §1.2 customer_user）。
 * 注意：openid / phone_ciphertext 属敏感字段，任何接口出参都不得直出（docs/接口文档.md §2.5）。
 */
@TableName("customer_user")
public class CustomerUser extends BaseEntity {

    private String wxAppId;
    private String openid;
    private String nickname;
    private String phoneCiphertext;
    private LocalDateTime phoneVerifiedAt;
    private Integer tokenVersion;
    private String status;

    public String getWxAppId() {
        return wxAppId;
    }

    public void setWxAppId(String wxAppId) {
        this.wxAppId = wxAppId;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhoneCiphertext() {
        return phoneCiphertext;
    }

    public void setPhoneCiphertext(String phoneCiphertext) {
        this.phoneCiphertext = phoneCiphertext;
    }

    public LocalDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void setPhoneVerifiedAt(LocalDateTime phoneVerifiedAt) {
        this.phoneVerifiedAt = phoneVerifiedAt;
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
