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
 * 后台认证（docs/接口文档.md §2.2 后台登录、§2.4 退出全部登录）。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<AuthDtos.AdminLoginResponse> login(
            @Valid @RequestBody AuthDtos.AdminLoginRequest request) {
        return R.ok(authService.adminLogin(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public R<AuthDtos.Accepted> logout(@RequestBody(required = false) Map<String, Object> body) {
        return R.ok(authService.logoutAdmin());
    }
}
