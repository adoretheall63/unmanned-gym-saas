package com.wrgym.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wrgym.auth.client.WechatIdentityClient;
import com.wrgym.auth.config.AuthProperties;
import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.entity.AdminUser;
import com.wrgym.auth.entity.CustomerUser;
import com.wrgym.auth.entity.Merchant;
import com.wrgym.auth.mapper.AdminUserMapper;
import com.wrgym.auth.mapper.CustomerUserMapper;
import com.wrgym.auth.mapper.MerchantMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import com.wrgym.security.auth.SecurityUtils;
import com.wrgym.security.jwt.JwtProperties;
import com.wrgym.security.jwt.JwtService;
import com.wrgym.security.ratelimit.LoginRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证服务（docs/接口文档.md §2.1–§2.4、§2.10）。
 *
 * <p>关键约束：
 * <ul>
 *   <li>登录失败统一 401（不区分用户不存在/密码错误），并限流；账号停用 403；商家停用 403。</li>
 *   <li>退出与改密通过原子递增 token_version 使全部旧 JWT 失效。</li>
 *   <li>JWT 只签发身份与版本；每次请求的状态/版本复核在 AccountStatusFilter 中完成。</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CustomerUserMapper customerUserMapper;
    private final AdminUserMapper adminUserMapper;
    private final MerchantMapper merchantMapper;
    private final WechatIdentityClient wechatIdentityClient;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthProperties authProperties;

    public AuthService(CustomerUserMapper customerUserMapper, AdminUserMapper adminUserMapper,
                       MerchantMapper merchantMapper, WechatIdentityClient wechatIdentityClient,
                       JwtService jwtService, JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder, LoginRateLimiter loginRateLimiter,
                       AuthProperties authProperties) {
        this.customerUserMapper = customerUserMapper;
        this.adminUserMapper = adminUserMapper;
        this.merchantMapper = merchantMapper;
        this.wechatIdentityClient = wechatIdentityClient;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
        this.authProperties = authProperties;
    }

    /** 2.1 微信登录：openid 唯一身份，首次登录自动建号。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.WechatLoginResponse wechatLogin(String code) {
        WechatIdentityClient.WechatIdentity identity = wechatIdentityClient.exchange(code);
        CustomerUser customer = customerUserMapper.selectOne(new LambdaQueryWrapper<CustomerUser>()
                .eq(CustomerUser::getWxAppId, identity.appId())
                .eq(CustomerUser::getOpenid, identity.openid()));
        if (customer == null) {
            customer = new CustomerUser();
            customer.setWxAppId(identity.appId());
            customer.setOpenid(identity.openid());
            customer.setTokenVersion(0);
            customer.setStatus("ACTIVE");
            customerUserMapper.insert(customer);
        } else if (!"ACTIVE".equals(customer.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        String token = jwtService.signCustomer(customer.getId(), customer.getTokenVersion(),
                customer.getNickname());
        AuthDtos.CustomerBrief brief = new AuthDtos.CustomerBrief(String.valueOf(customer.getId()),
                customer.getNickname(), customer.getPhoneVerifiedAt() != null);
        return new AuthDtos.WechatLoginResponse(token, "Bearer", jwtProperties.getExpireSeconds(), brief);
    }

    /** 2.2 后台登录：BCrypt 校验 + 登录限流 + 商家状态检查。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.AdminLoginResponse adminLogin(String username, String password) {
        loginRateLimiter.check("ADMIN", username);
        AdminUser admin = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username));
        if (admin == null) {
            // 不区分"用户不存在"和"密码错误"，避免账号枚举；失败计入限流
            loginRateLimiter.recordFailure("ADMIN", username);
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            loginRateLimiter.recordFailure("ADMIN", username);
            log.info("admin login failed username={} requestId={}", username,
                    com.wrgym.common.web.RequestContext.currentRequestId());
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        loginRateLimiter.reset("ADMIN", username);
        Merchant merchant = merchantMapper.selectById(admin.getMerchantId());
        if (merchant == null || !"ACTIVE".equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "商家已停用");
        }
        String token = jwtService.signAdmin(admin.getId(), admin.getMerchantId(), admin.getTokenVersion(),
                admin.getUsername(), admin.getPermissions());
        AuthDtos.AdminBrief brief = new AuthDtos.AdminBrief(String.valueOf(admin.getId()),
                String.valueOf(admin.getMerchantId()), admin.getUsername(), admin.getRole(),
                admin.getPermissions() == null ? List.of() : admin.getPermissions());
        return new AuthDtos.AdminLoginResponse(token, "Bearer", jwtProperties.getExpireSeconds(), brief);
    }

    /** 2.3 顾客退出全部登录：token_version 递增，旧 JWT 全部失效。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.Accepted logoutCustomer() {
        long customerId = SecurityUtils.currentCustomerId();
        int affected = customerUserMapper.bumpTokenVersion(customerId);
        if (affected != 1) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return new AuthDtos.Accepted(true);
    }

    /** 2.4 后台退出全部登录。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.Accepted logoutAdmin() {
        long adminId = SecurityUtils.currentAdminId();
        int affected = adminUserMapper.bumpTokenVersion(adminId);
        if (affected != 1) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return new AuthDtos.Accepted(true);
    }

    /**
     * 2.10 修改本人后台密码：校验旧密码，新密码禁止复用用户名/旧密码，
     * 只保存 BCrypt，并与 token_version 递增同事务（全部旧登录失效）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.Accepted changePassword(String oldPassword, String newPassword) {
        long adminId = SecurityUtils.currentAdminId();
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (!passwordEncoder.matches(oldPassword, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "旧密码不正确");
        }
        if (newPassword.equals(admin.getUsername()) || newPassword.equals(oldPassword)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "新密码不能与用户名或旧密码相同",
                    List.of(new com.wrgym.common.api.FieldErrorItem("newPassword", "禁止复用用户名或旧密码")));
        }
        String newHash = passwordEncoder.encode(newPassword);
        int affected = adminUserMapper.changePassword(adminId, admin.getPasswordHash(), newHash);
        if (affected != 1) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        return new AuthDtos.Accepted(true);
    }
}
