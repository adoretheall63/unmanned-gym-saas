package com.wrgym.common.security;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;

/**
 * 当前请求主体（线程内）。由 gym-security 的 JWT 过滤器在认证成功后写入、请求结束时清理。
 *
 * <p>只用于需要主体标识的通用组件（幂等、审计）；业务代码取完整登录信息请用
 * {@code com.wrgym.security.auth.SecurityUtils}。
 */
public final class PrincipalRef {

    private static final ThreadLocal<SecurityPrincipal> CURRENT = new ThreadLocal<>();

    private PrincipalRef() {
    }

    public static void set(SecurityPrincipal principal) {
        CURRENT.set(principal);
    }

    public static SecurityPrincipal currentOrNull() {
        return CURRENT.get();
    }

    public static SecurityPrincipal current() {
        SecurityPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
