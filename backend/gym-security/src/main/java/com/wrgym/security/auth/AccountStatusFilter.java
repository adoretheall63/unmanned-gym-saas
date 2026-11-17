package com.wrgym.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.R;
import com.wrgym.common.port.AccountPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每请求账号状态与 token_version 复核（docs/接口文档.md §1.1 / §2.9）。
 *
 * <p>JWT 只证明"曾经登录"；账号停用、退出全部登录、改密后旧令牌必须立即失效。
 * 因此每个带身份请求都通过 {@link AccountPort} 读取当前状态（当前读、不加缓存）。
 * 若业务模块尚未提供 AccountPort 实现（例如只有基础设施分支），本过滤器跳过，不阻断开发。
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {

    private final ObjectProvider<AccountPort> accountPortProvider;
    private final ObjectMapper objectMapper;

    public AccountStatusFilter(ObjectProvider<AccountPort> accountPortProvider, ObjectMapper objectMapper) {
        this.accountPortProvider = accountPortProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof LoginUser loginUser)) {
            chain.doFilter(request, response);
            return;
        }
        AccountPort accountPort = accountPortProvider.getIfAvailable();
        if (accountPort == null) {
            chain.doFilter(request, response);
            return;
        }
        AccountPort.AccountStatus status = loginUser.isAdmin()
                ? accountPort.loadAdmin(loginUser.id())
                : accountPort.loadCustomer(loginUser.id());
        boolean valid = status != null
                && status.active()
                && status.tokenVersion() == loginUser.tokenVersion();
        if (!valid) {
            response.setStatus(ErrorCode.UNAUTHENTICATED.httpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), R.fail(ErrorCode.UNAUTHENTICATED));
            return;
        }
        chain.doFilter(request, response);
    }
}
