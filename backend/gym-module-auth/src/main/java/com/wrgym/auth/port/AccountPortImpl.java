package com.wrgym.auth.port;

import com.wrgym.auth.entity.AdminUser;
import com.wrgym.auth.entity.CustomerUser;
import com.wrgym.auth.mapper.AdminUserMapper;
import com.wrgym.auth.mapper.CustomerUserMapper;
import com.wrgym.common.port.AccountPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AccountPort 实现（由 gym-module-auth 提供）：供 gym-security 的每请求状态/版本复核使用。
 */
@Service
public class AccountPortImpl implements AccountPort {

    private final CustomerUserMapper customerUserMapper;
    private final AdminUserMapper adminUserMapper;

    public AccountPortImpl(CustomerUserMapper customerUserMapper, AdminUserMapper adminUserMapper) {
        this.customerUserMapper = customerUserMapper;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public AccountStatus loadCustomer(long customerId) {
        CustomerUser customer = customerUserMapper.selectById(customerId);
        if (customer == null) {
            return null;
        }
        return new AccountStatus(customer.getId(), null, customer.getStatus(),
                customer.getTokenVersion() == null ? 0 : customer.getTokenVersion(), null, List.of());
    }

    @Override
    public AccountStatus loadAdmin(long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            return null;
        }
        return new AccountStatus(admin.getId(), admin.getMerchantId(), admin.getStatus(),
                admin.getTokenVersion() == null ? 0 : admin.getTokenVersion(), admin.getRole(),
                admin.getPermissions() == null ? List.of() : admin.getPermissions());
    }
}
