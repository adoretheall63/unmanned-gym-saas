package com.wrgym.security.auth;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前登录主体。未登录或身份类型不符时抛 401/403，Controller 无需自行判断。
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
}
