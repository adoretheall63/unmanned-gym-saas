package com.wrgym.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * §2 认证与个人资料 的请求/响应 DTO（docs/接口文档.md L105–L727）。
 *
 * <p>契约口径：
 * <ul>
 *   <li>业务 ID 一律字符串（{@code ^[1-9][0-9]{0,18}$}）；</li>
 *   <li>{@code expiresIn} 为整数秒，不转字符串；</li>
 *   <li>可为 null 的必有字段必须显式输出 null（如 nickname / phoneMasked）；</li>
 *   <li>登录响应字段名分别为 {@code customer} / {@code admin}，不能混用。</li>
 * </ul>
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ---------- 请求 ----------

    /** 2.1 微信登录：wx.login 临时 code，后端换身份。 */
    public record WechatLoginRequest(
            @NotBlank @Size(min = 1, max = 256) String code) {
    }

    /** 2.2 后台登录。 */
    public record AdminLoginRequest(
            @NotBlank @Size(min = 1, max = 64) String username,
            @NotBlank @Size(min = 1, max = 128) String password) {
    }

    /** 2.6 修改昵称：只接受 nickname，其他身份字段不开放。 */
    public record UpdateNicknameRequest(
            @NotBlank @Size(min = 1, max = 64) String nickname) {
    }

    /** 2.7 绑定手机号：一次性兑换 code，不能直接提交手机号。 */
    public record PhoneBindingRequest(
            @NotBlank @Size(min = 1, max = 512) String phoneCode) {
    }

    /** 2.10 修改本人后台密码。 */
    public record ChangePasswordRequest(
            @NotBlank @Size(min = 1, max = 128) String oldPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    // ---------- 响应 ----------

    /** 2.1 登录响应里的顾客摘要。 */
    public record CustomerBrief(String id, String nickname, boolean phoneVerified) {
    }

    /** 2.2 登录响应里的后台身份摘要。 */
    public record AdminBrief(String id, String merchantId, String username, String role,
                             List<String> permissions) {
    }

    /** 2.1 微信登录响应（字段名必须是 customer）。 */
    public record WechatLoginResponse(String accessToken, String tokenType, long expiresIn,
                                      CustomerBrief customer) {
    }

    /** 2.2 后台登录响应（字段名必须是 admin）。 */
    public record AdminLoginResponse(String accessToken, String tokenType, long expiresIn,
                                     AdminBrief admin) {
    }

    /** 2.5 / 2.6 / 2.7 / 2.8 统一的本人资料视图（不含 openid/手机号密文/人脸）。 */
    public record CustomerProfile(String id, String nickname, String phoneMasked, boolean phoneVerified) {
    }

    /** 2.9 后台登录身份。 */
    public record AdminProfile(String id, String merchantId, String username, String role,
                               List<String> permissions) {
    }

    /** 2.3 / 2.4 / 2.10 的受理结果。 */
    public record Accepted(boolean accepted) {
    }
}
