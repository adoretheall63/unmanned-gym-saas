package com.wrgym.auth.controller;

import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.service.ProfileService;
import com.wrgym.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客个人资料（docs/接口文档.md §2.5–§2.8）。
 *
 * <p>2.6/2.7/2.8 必须带 Idempotency-Key；缺失由全局异常映射为 400 VALIDATION_ERROR。
 */
@RestController
@RequestMapping("/api/v1/app/me")
public class AppMeController {

    private final ProfileService profileService;

    public AppMeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public R<AuthDtos.CustomerProfile> me() {
        return R.ok(profileService.getProfile());
    }

    @PatchMapping
    public R<AuthDtos.CustomerProfile> updateNickname(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthDtos.UpdateNicknameRequest request) {
        return R.ok(profileService.updateNickname(idempotencyKey, request.nickname()));
    }

    @PostMapping("/phone-binding")
    public R<AuthDtos.CustomerProfile> bindPhone(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthDtos.PhoneBindingRequest request) {
        return R.ok(profileService.bindPhone(idempotencyKey, request.phoneCode()));
    }

    @DeleteMapping("/phone-binding")
    public R<AuthDtos.CustomerProfile> unbindPhone(
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return R.ok(profileService.unbindPhone(idempotencyKey));
    }
}
