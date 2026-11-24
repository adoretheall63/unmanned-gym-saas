package com.wrgym.security.auth;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.port.AccountPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 读取当前登录主体。未登录或身份类型不符时抛 401/403，Controller 无需自行判断。
 *
 * <p>角色（ADMIN / OPERATOR）与权限码取 {@link AccountContext} 的数据库权威值，
 * 不信任 JWT 内的旧声明（docs/接口文档.md §2.9）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return loginUser;
    }

    public static LoginUser currentCustomer() {
        LoginUser loginUser = currentUser();
        if (!loginUser.isCustomer()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return loginUser;
    }

    public static LoginUser currentAdmin() {
        LoginUser loginUser = currentUser();
        if (!loginUser.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return loginUser;
    }

    public static long currentCustomerId() {
        return currentCustomer().id();
    }

    public static long currentAdminId() {
        return currentAdmin().id();
    }

    /** 后台操作必须用 JWT 里的 merchantId 做隔离。 */
    public static long currentMerchantId() {
        LoginUser loginUser = currentAdmin();
        if (loginUser.merchantId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return loginUser.merchantId();
    }

    /** 当前后台账号的数据库权威状态（角色/权限）；缺失说明过滤器未执行，按未认证处理。 */
    public static AccountPort.AccountStatus currentAdminStatus() {
        currentAdmin();
        AccountPort.AccountStatus status = AccountContext.currentOrNull();
        if (status == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return status;
    }

    /** 仅 ADMIN 可做的操作（员工/商家配置、退款、卡补偿等关键操作）。 */
    public static AccountPort.AccountStatus requireAdminRole() {
        AccountPort.AccountStatus status = currentAdminStatus();
        if (!"ADMIN".equals(status.role())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return status;
    }

    /** 校验某个权限码（ADMIN 恒通过；OPERATOR 需在权限集合内）。 */
    public static void requirePermission(String permissionCode) {
        AccountPort.AccountStatus status = currentAdminStatus();
        if ("ADMIN".equals(status.role())) {
            return;
        }
        List<String> permissions = status.permissions() == null ? List.of() : status.permissions();
        if (!permissions.contains(permissionCode)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
