package com.wrgym.security.auth;

import java.util.List;

/**
 * 当前已认证主体（来自 JWT）。后台身份必须通过 merchantId 做商家隔离，
 * 禁止信任请求参数里的 merchantId（docs/接口文档.md §1.1 隔离）。
 */
public record LoginUser(long id, Type type, String name, Long merchantId, int tokenVersion, List<String> permissions) {

    public enum Type { CUSTOMER, ADMIN }

    public boolean isAdmin() {
        return type == Type.ADMIN;
    }

    public boolean isCustomer() {
        return type == Type.CUSTOMER;
    }
}
