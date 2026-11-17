package com.wrgym.auth.controller;

import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.service.AuthService;
import com.wrgym.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 顾客认证（docs/接口文档.md §2.1 微信登录、§2.3 退出全部登录）。
 */
@RestController
@RequestMapping("/api/v1/app/auth")
public class AppAuthController {

    private final AuthService authService;

    public AppAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat-login")
    public R<AuthDtos.WechatLoginResponse> wechatLogin(
            @Valid @RequestBody AuthDtos.WechatLoginRequest request) {
        return R.ok(authService.wechatLogin(request.code()));
    }

    @PostMapping("/logout")
    public R<AuthDtos.Accepted> logout(@RequestBody(required = false) Map<String, Object> body) {
        return R.ok(authService.logoutCustomer());
    }
}
