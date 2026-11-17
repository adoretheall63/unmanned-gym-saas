package com.wrgym.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wrgym.common.entity.BaseEntity;

/**
 * 商家（docs/数据库设计.md §1.1 merchant）。后台登录时校验商家状态（DISABLED 不允许登录）。
 */
@TableName("merchant")
public class Merchant extends BaseEntity {

    private String name;
    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
