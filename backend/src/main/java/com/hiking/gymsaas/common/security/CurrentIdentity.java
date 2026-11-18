package com.hiking.gymsaas.common.security;

import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentIdentity {
    private CurrentIdentity() { }

    public static Identity get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Identity identity)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return identity;
    }

    public static Identity admin() {
        var identity = get();
        if (identity.type() != Identity.Type.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        return identity;
    }

    public static Identity customer() {
        var identity = get();
        if (identity.type() != Identity.Type.CUSTOMER) throw new BusinessException(ErrorCode.FORBIDDEN);
        return identity;
    }

    public static long merchantId() { return admin().merchantId(); }

    public static void requireMerchant(long resourceMerchantId) {
        if (merchantId() != resourceMerchantId) throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    public static void requireOwner(long resourceUserId) {
        if (customer().id() != resourceUserId) throw new BusinessException(ErrorCode.NOT_FOUND);
    }
}
