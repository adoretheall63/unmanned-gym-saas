package com.wrgym.common.port;

import java.util.List;

/**
 * 跨模块账号状态查询端口（gym-common 只定义接口，实现由 gym-module-auth 提供）。
 *
 * <p>用途：每个受保护请求重新校验账号启用状态与 {@code token_version}（docs/接口文档.md §1.1 / §2.9），
 * 不能信任 JWT 内的快照；JWT 只证明"曾登录过"。
 */
public interface AccountPort {

    AccountStatus loadCustomer(long customerId);

    AccountStatus loadAdmin(long adminId);

    /**
     * 账号当前状态。
     *
     * @param id          主体ID
     * @param merchantId  后台账号所属商家（顾客为 null）
     * @param status      ACTIVE / DISABLED
     * @param tokenVersion 当前登录版本，JWT 中的 ver 必须一致
     * @param role        ADMIN / OPERATOR（顾客为 null）
     * @param permissions 后台权限码（顾客为空）
     */
    record AccountStatus(long id, Long merchantId, String status, int tokenVersion,
                         String role, List<String> permissions) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }
}
