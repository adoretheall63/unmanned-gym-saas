package com.hiking.gymsaas.account.controller;

import com.hiking.gymsaas.account.model.dto.AuthRequests;
import com.hiking.gymsaas.account.model.vo.AuthViews;
import com.hiking.gymsaas.account.service.AuthService;
import com.hiking.gymsaas.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | mock-demo) & !prod & !production & !real & !bootstrap")
@ConditionalOnProperty(prefix = "app.security.dev", name = "enabled", havingValue = "true")
public class DevAuthController {
    private final AuthService auth;
    public DevAuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/dev/auth/customer-login")
    public ApiResponse<AuthViews.CustomerToken> login(@Valid @RequestBody AuthRequests.DevLogin input,
            @RequestHeader(value = "X-Dev-Key", required = false) String key, HttpServletRequest request) {
        return ApiResponse.ok(auth.devLogin(input, key, request.getRemoteAddr()));
    }
}
