package com.hiking.gymsaas.account.service.impl;

import com.hiking.gymsaas.account.mapper.IdentityMapper;
import com.hiking.gymsaas.account.model.entity.AdminUser;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.security.Identity;
import com.hiking.gymsaas.account.service.IdentityService;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class IdentityServiceImpl implements IdentityService {
    public static final Set<String> PERMISSIONS = Set.of("STORE_READ", "STORE_CONTROL", "CUSTOMER_READ",
            "ORDER_READ", "VISIT_READ", "CARD_READ", "FINANCE_READ", "DEVICE_READ", "DEVICE_CONTROL",
            "VIDEO_READ", "LIGHT_CONTROL", "REVIEW_READ", "REVIEW_HANDLE", "REPORT_READ", "REPORT_REFRESH", "AUDIT_READ");
    private final IdentityMapper mapper;

    public IdentityServiceImpl(IdentityMapper mapper) { this.mapper = mapper; }

    @Override
    public Identity load(Identity.Type type, long id) { return read(type, id, false); }

    @Override
    public Identity recheck(Identity expected, boolean lock) {
        var current = read(expected.type(), expected.id(), lock);
        if (current.tokenVersion() != expected.tokenVersion()
                || !java.util.Objects.equals(current.merchantId(), expected.merchantId())
                || !java.util.Objects.equals(current.role(), expected.role()) || !current.permissions().equals(expected.permissions())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return current;
    }

    private Identity read(Identity.Type type, long id, boolean lock) {
        if (type == Identity.Type.CUSTOMER) {
            var user = lock ? mapper.lockCustomer(id) : mapper.customer(id);
            if (user == null || !"ACTIVE".equals(user.getStatus())) fail();
            return new Identity(type, id, null, user.getTokenVersion(), null, List.of());
        }
        var user = lock ? mapper.lockAdmin(id) : mapper.admin(id);
        if (user == null || !"ACTIVE".equals(user.getStatus())) fail();
        var merchant = lock ? mapper.lockMerchant(user.getMerchantId()) : mapper.merchant(user.getMerchantId());
        if (merchant == null || !"ACTIVE".equals(merchant.getStatus())) fail();
        return adminIdentity(user);
    }

    public static Identity adminIdentity(AdminUser user) {
        if (!Set.of("ADMIN", "OPERATOR").contains(user.getRole())) fail();
        var permissions = user.getPermissions() == null ? List.<String>of()
                : user.getPermissions().stream().filter(PERMISSIONS::contains).distinct().sorted().toList();
        return new Identity(Identity.Type.ADMIN, user.getId(), user.getMerchantId(),
                user.getTokenVersion(), user.getRole(), permissions);
    }

    private static void fail() { throw new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
