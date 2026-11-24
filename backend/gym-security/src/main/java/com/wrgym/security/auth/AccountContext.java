package com.wrgym.security.auth;

import com.wrgym.common.port.AccountPort;

/**
 * 当前请求的"账号权威状态"（来自数据库当前读，而不是 JWT 快照）。
 *
 * <p>由 {@link AccountStatusFilter} 在复核通过后写入，请求结束时清理；
 * 业务层用它判断后台角色（ADMIN / OPERATOR）与权限码，避免信任 JWT 内可能过期的声明。
 */
public final class AccountContext {

    private static final ThreadLocal<AccountPort.AccountStatus> CURRENT = new ThreadLocal<>();

    private AccountContext() {
    }

    public static void set(AccountPort.AccountStatus status) {
        CURRENT.set(status);
    }

    public static AccountPort.AccountStatus currentOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
