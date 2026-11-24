package com.wrgym.common.security;

import java.util.List;
import java.util.Set;

/**
 * 后台权限码白名单（docs/接口文档.md §1.3）。
 *
 * <p>操作员权限从 {@code admin_user.permissions} 白名单数组取得，不接受任意字符串；
 * ADMIN 覆盖全部权限码，新建/调整 ADMIN 时 permissions 必须为空数组（③.5 契约）。
 */
public final class PermissionCodes {

    public static final String STORE_READ = "STORE_READ";
    public static final String STORE_CONTROL = "STORE_CONTROL";
    public static final String CUSTOMER_READ = "CUSTOMER_READ";
    public static final String ORDER_READ = "ORDER_READ";
    public static final String VISIT_READ = "VISIT_READ";
    public static final String CARD_READ = "CARD_READ";
    public static final String FINANCE_READ = "FINANCE_READ";
    public static final String DEVICE_READ = "DEVICE_READ";
    public static final String DEVICE_CONTROL = "DEVICE_CONTROL";
    public static final String VIDEO_READ = "VIDEO_READ";
    public static final String LIGHT_CONTROL = "LIGHT_CONTROL";
    public static final String REVIEW_READ = "REVIEW_READ";
    public static final String REVIEW_HANDLE = "REVIEW_HANDLE";
    public static final String REPORT_READ = "REPORT_READ";
    public static final String REPORT_REFRESH = "REPORT_REFRESH";
    public static final String AUDIT_READ = "AUDIT_READ";

    /** 可授予 OPERATOR 的全部权限码。 */
    public static final Set<String> ALL = Set.copyOf(List.of(
            STORE_READ, STORE_CONTROL, CUSTOMER_READ, ORDER_READ, VISIT_READ, CARD_READ, FINANCE_READ,
            DEVICE_READ, DEVICE_CONTROL, VIDEO_READ, LIGHT_CONTROL, REVIEW_READ, REVIEW_HANDLE,
            REPORT_READ, REPORT_REFRESH, AUDIT_READ));

    private PermissionCodes() {
    }

    public static boolean isValid(String code) {
        return code != null && ALL.contains(code);
    }
}
