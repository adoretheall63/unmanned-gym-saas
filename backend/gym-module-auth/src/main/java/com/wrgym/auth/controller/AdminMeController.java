package com.wrgym.auth.controller;

import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.service.AuthService;
import com.wrgym.auth.service.AdminMeService;
import com.wrgym.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台本人身份与密码（docs/接口文档.md §2.9、§2.10）。
 */
@RestController
@RequestMapping("/api/v1/admin/me")
public class AdminMeController {

    private final AdminMeService adminMeService;
    private final AuthService authService;

    public AdminMeController(AdminMeService adminMeService, AuthService authService) {
        this.adminMeService = adminMeService;
        this.authService = authService;
    }

    @GetMapping
    public R<AuthDtos.AdminProfile> me() {
        return R.ok(adminMeService.currentAdminProfile());
    }

    @PostMapping("/password")
    public R<AuthDtos.Accepted> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        return R.ok(authService.changePassword(request.oldPassword(), request.newPassword()));
    }
}
