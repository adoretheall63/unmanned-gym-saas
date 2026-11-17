package com.wrgym.common.security;

/**
 * 最小主体标识（gym-common 视角）。完整登录信息（姓名、商家、权限）在 gym-security 的 LoginUser 中，
 * 本类型只供 common 层的幂等、审计等组件使用，避免 common 反向依赖 security。
 *
 * @param id   主体ID
 * @param type CUSTOMER / ADMIN / DEV
 */
public record SecurityPrincipal(long id, String type) {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";
    public static final String DEV = "DEV";
}
