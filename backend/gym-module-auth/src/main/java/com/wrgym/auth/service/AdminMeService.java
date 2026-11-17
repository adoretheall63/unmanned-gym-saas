package com.wrgym.auth.service;

import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.entity.AdminUser;
import com.wrgym.auth.mapper.AdminUserMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import com.wrgym.security.auth.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 2.9 读取后台登录身份。
 *
 * <p>文档要求"后台每次请求仍重新校验账号状态与 token_version"——请求级校验由
 * {@code AccountStatusFilter} 完成，本方法只读当前已复核过的账号并返回白名单字段。
 */
@Service
public class AdminMeService {

    private final AdminUserMapper adminUserMapper;

    public AdminMeService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    public AuthDtos.AdminProfile currentAdminProfile() {
        long adminId = SecurityUtils.currentAdminId();
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null || !"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return new AuthDtos.AdminProfile(String.valueOf(admin.getId()),
                String.valueOf(admin.getMerchantId()), admin.getUsername(), admin.getRole(),
                admin.getPermissions() == null ? List.of() : admin.getPermissions());
    }
}
