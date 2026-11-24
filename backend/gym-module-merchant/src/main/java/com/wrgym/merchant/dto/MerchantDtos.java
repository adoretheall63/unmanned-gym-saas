package com.wrgym.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * §3 商家与员工权限 的请求/响应 DTO（docs/接口文档.md §3.1–§3.8）。
 *
 * <p>口径：ID 为字符串；分页字段 page/pageSize/total/items；写操作带 reason（审计）；
 * 管理员/操作员枚举 ADMIN|OPERATOR；权限码必须在白名单内，ADMIN 必须传空数组。
 */
public final class MerchantDtos {

    private MerchantDtos() {
    }

    // ---------- 3.1 / 3.2 商家 ----------

    public record MerchantVO(String id, String name, String status) {
    }

    /** 3.2 只允许改展示名；不能在此停用商家或改变归属。 */
    public record UpdateMerchantRequest(
            @NotBlank @Size(min = 1, max = 100) String name,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    // ---------- 3.3 / 3.4 员工 ----------

    public record StaffVO(String id, String username, String role, List<String> permissions,
                          String status, LocalDateTime createdAt) {
    }

    /** 3.5 创建员工：merchantId 永不从请求体取。 */
    public record CreateStaffRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,64}$", message = "登录名只允许 4–64 位字母数字下划线")
            String username,
            @NotBlank @Size(min = 12, max = 128) String initialPassword,
            @NotBlank @Pattern(regexp = "^(ADMIN|OPERATOR)$", message = "role 只能是 ADMIN 或 OPERATOR")
            String role,
            @NotNull List<String> permissions,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    /** 3.6 调整角色权限。 */
    public record UpdateStaffRequest(
            @NotBlank @Pattern(regexp = "^(ADMIN|OPERATOR)$", message = "role 只能是 ADMIN 或 OPERATOR")
            String role,
            @NotNull List<String> permissions,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    /** 3.7 启停账号。 */
    public record UpdateStaffStatusRequest(
            @NotBlank @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "status 只能是 ACTIVE 或 DISABLED")
            String status,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    /** 3.8 管理员重置密码：只存 BCrypt，不回显。 */
    public record ResetStaffPasswordRequest(
            @NotBlank @Size(min = 12, max = 128) String newPassword,
            @NotBlank @Size(min = 1, max = 500) String reason) {
    }

    public record StaffListQuery(int page, int pageSize, String status) {
    }

    /** 3.8 的响应只有 accepted。 */
    public record Accepted(boolean accepted) {
    }
}
