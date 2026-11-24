package com.wrgym.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wrgym.common.entity.BaseEntity;

/**
 * 商家（docs/数据库设计.md §1.1 merchant）。本模块只改展示名（name），
 * 停用/归属变更不通过接口进行（docs/接口文档.md §3.2）。
 */
@TableName("merchant")
public class MerchantEntity extends BaseEntity {

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
