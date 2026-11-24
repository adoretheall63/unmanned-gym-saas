package com.wrgym.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.audit.AuditWriter;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.security.PermissionCodes;
import com.wrgym.common.web.Etags;
import com.wrgym.common.web.Ids;
import com.wrgym.merchant.dto.MerchantDtos;
import com.wrgym.merchant.entity.StaffEntity;
import com.wrgym.merchant.mapper.StaffMapper;
import com.wrgym.security.auth.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 员工管理（docs/接口文档.md §3.3–§3.8）。
 *
 * <p>关键约束：
 * <ul>
 *   <li>商家隔离：merchantId 只来自 JWT，所有查询/更新带商家条件；跨商家一律 404。</li>
 *   <li>权限白名单：OPERATOR 只能取 §1.3 权限码；ADMIN 的 permissions 必须为空数组。</li>
 *   <li>最后一位启用管理员保护：调整/停用前串行锁定本商家管理员集合，禁止把启用中的 ADMIN 降级或停用至 0 位。</li>
 *   <li>角色/权限/状态/密码变更都递增 token_version，旧 JWT 立即失效；不返回 password_hash。</li>
 *   <li>写操作必须带 Idempotency-Key；3.6/3.7 还必须带 If-Match（锁内重算比对）。</li>
 * </ul>
 */
@Service
public class StaffService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StaffMapper staffMapper;
    private final AuditWriter auditWriter;
    private final IdempotencyService idempotencyService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public StaffService(StaffMapper staffMapper, AuditWriter auditWriter, IdempotencyService idempotencyService,
                        PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.staffMapper = staffMapper;
        this.auditWriter = auditWriter;
        this.idempotencyService = idempotencyService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    public record StaffView(MerchantDtos.StaffVO value, String etag) {
    }

    /** 3.3 员工列表（按 JWT 商家过滤，分页）。 */
    public PageResult<MerchantDtos.StaffVO> list(MerchantDtos.StaffListQuery query) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        int page = Math.max(1, query.page());
        int pageSize = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);
        LambdaQueryWrapper<StaffEntity> wrapper = new LambdaQueryWrapper<StaffEntity>()
                .eq(StaffEntity::getMerchantId, merchantId)
                .eq(query.status() != null && !query.status().isBlank(), StaffEntity::getStatus, query.status())
                .orderByAsc(StaffEntity::getId);
        Page<StaffEntity> result = staffMapper.selectPage(Page.of(page, pageSize), wrapper);
        List<MerchantDtos.StaffVO> items = result.getRecords().stream().map(this::toVo).toList();
        return new PageResult<>(items, result.getTotal(), page, pageSize);
    }

    /** 3.4 员工详情：返回 ETag 供 3.6/3.7 使用。 */
    public StaffView detail(String staffIdRaw) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long staffId = Ids.parse(staffIdRaw, "staffId");
        StaffEntity staff = staffMapper.selectOne(new LambdaQueryWrapper<StaffEntity>()
                .eq(StaffEntity::getId, staffId)
                .eq(StaffEntity::getMerchantId, merchantId));
        if (staff == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new StaffView(toVo(staff), etagFor(staff));
    }

    /** 3.5 创建员工：201，merchantId 从 JWT 写入，只存 BCrypt。 */
    public MerchantDtos.StaffVO create(String idempotencyKey, MerchantDtos.CreateStaffRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        validatePermissions(request.role(), request.permissions());
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = request.username() + "\n" + request.role() + "\n" + request.permissions()
                + "\n" + request.reason();
        return idempotencyService.execute("admin.staff.create", key, requestBody,
                MerchantDtos.StaffVO.class, () -> {
                    StaffEntity staff = new StaffEntity();
                    staff.setMerchantId(merchantId);
                    staff.setUsername(request.username());
                    staff.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
                    staff.setRole(request.role());
                    staff.setPermissions(request.role().equals("ADMIN") ? List.of() : List.copyOf(request.permissions()));
                    staff.setTokenVersion(0);
                    staff.setStatus("ACTIVE");
                    try {
                        staffMapper.insert(staff);
                    } catch (DuplicateKeyException duplicate) {
                        throw new BizException(ErrorCode.STATE_CONFLICT, "登录名已存在");
                    }
                    auditWriter.record(merchantId, "STAFF_CREATED", "ADMIN", staff.getId(), adminId,
                            request.reason(), Map.of("username", request.username(), "role", request.role(),
                                    "permissions", request.permissions()));
                    return toVo(staff);
                });
    }

    /** 3.6 调整角色权限：幂等 + ETag + 管理员集合串行锁 + 最后管理员保护 + 版本递增。 */
    public StaffView updateRole(String staffIdRaw, String ifMatch, String idempotencyKey,
                                MerchantDtos.UpdateStaffRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long staffId = Ids.parse(staffIdRaw, "staffId");
        validatePermissions(request.role(), request.permissions());
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = staffId + "\n" + request.role() + "\n" + request.permissions() + "\n" + request.reason();
        MerchantDtos.StaffVO updated = idempotencyService.execute("admin.staff.role", key, requestBody,
                MerchantDtos.StaffVO.class, () -> {
                    List<StaffEntity> activeAdmins = staffMapper.lockActiveAdmins(merchantId);
                    StaffEntity target = staffMapper.selectTenantForUpdate(staffId, merchantId);
                    if (target == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, etagFor(target));
                    boolean removingActiveAdmin = "ADMIN".equals(target.getRole())
                            && "ACTIVE".equals(target.getStatus())
                            && !"ADMIN".equals(request.role());
                    if (removingActiveAdmin && activeAdmins.size() <= 1) {
                        throw new BizException(ErrorCode.STATE_CONFLICT, "不能撤销最后一位启用管理员");
                    }
                    staffMapper.updateRolePermissions(staffId, merchantId, request.role(),
                            toJson(request.role().equals("ADMIN") ? List.of() : request.permissions()));
                    auditWriter.record(merchantId, "STAFF_ROLE_CHANGED", "ADMIN", staffId, adminId,
                            request.reason(), Map.of("beforeRole", target.getRole(),
                                    "beforePermissions", target.getPermissions() == null ? List.of() : target.getPermissions(),
                                    "afterRole", request.role(),
                                    "afterPermissions", request.permissions()));
                    return toVo(staffMapper.selectById(staffId));
                });
        StaffEntity current = staffMapper.selectById(staffId);
        return new StaffView(updated, etagFor(current));
    }

    /** 3.7 启停账号：停用立即失效旧 JWT；禁止停用最后一位启用管理员。 */
    public StaffView updateStatus(String staffIdRaw, String ifMatch, String idempotencyKey,
                                  MerchantDtos.UpdateStaffStatusRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long staffId = Ids.parse(staffIdRaw, "staffId");
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = staffId + "\n" + request.status() + "\n" + request.reason();
        MerchantDtos.StaffVO updated = idempotencyService.execute("admin.staff.status", key, requestBody,
                MerchantDtos.StaffVO.class, () -> {
                    List<StaffEntity> activeAdmins = staffMapper.lockActiveAdmins(merchantId);
                    StaffEntity target = staffMapper.selectTenantForUpdate(staffId, merchantId);
                    if (target == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, etagFor(target));
                    boolean disablingActiveAdmin = "DISABLED".equals(request.status())
                            && "ADMIN".equals(target.getRole())
                            && "ACTIVE".equals(target.getStatus());
                    if (disablingActiveAdmin && activeAdmins.size() <= 1) {
                        throw new BizException(ErrorCode.STATE_CONFLICT, "不能停用最后一位启用管理员");
                    }
                    staffMapper.updateStatus(staffId, merchantId, request.status());
                    auditWriter.record(merchantId, "STAFF_STATUS_CHANGED", "ADMIN", staffId, adminId,
                            request.reason(), Map.of("beforeStatus", target.getStatus(),
                                    "afterStatus", request.status()));
                    return toVo(staffMapper.selectById(staffId));
                });
        StaffEntity current = staffMapper.selectById(staffId);
        return new StaffView(updated, etagFor(current));
    }

    /** 3.8 管理员重置员工密码：只存 BCrypt、递增版本；幂等快照只保存 accepted。 */
    public MerchantDtos.Accepted resetPassword(String staffIdRaw, String idempotencyKey,
                                               MerchantDtos.ResetStaffPasswordRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long staffId = Ids.parse(staffIdRaw, "staffId");
        String key = idempotencyService.requireKey(idempotencyKey);
        // 摘要包含新密码（只以不可逆摘要参与幂等判定），快照只保存 accepted，不落密码
        String requestBody = staffId + "\n" + request.newPassword() + "\n" + request.reason();
        return idempotencyService.execute("admin.staff.password-reset", key, requestBody,
                MerchantDtos.Accepted.class, () -> {
                    StaffEntity target = staffMapper.selectTenantForUpdate(staffId, merchantId);
                    if (target == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    staffMapper.resetPassword(staffId, merchantId, passwordEncoder.encode(request.newPassword()));
                    auditWriter.record(merchantId, "STAFF_PASSWORD_RESET", "ADMIN", staffId, adminId,
                            request.reason(), Map.of("username", target.getUsername()));
                    return new MerchantDtos.Accepted(true);
                });
    }

    // ---------- 内部 ----------

    private void validatePermissions(String role, List<String> permissions) {
        if (permissions == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "permissions 必填",
                    List.of(new FieldErrorItem("permissions", "必填数组")));
        }
        if ("ADMIN".equals(role)) {
            if (!permissions.isEmpty()) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "ADMIN 的 permissions 必须为空数组",
                        List.of(new FieldErrorItem("permissions", "ADMIN 覆盖全部权限码，传空数组")));
            }
            return;
        }
        List<FieldErrorItem> errors = new ArrayList<>();
        for (String code : permissions) {
            if (!PermissionCodes.isValid(code)) {
                errors.add(new FieldErrorItem("permissions", "未知权限码: " + code));
            }
        }
        if (new HashSet<>(permissions).size() != permissions.size()) {
            errors.add(new FieldErrorItem("permissions", "权限码不能重复"));
        }
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "权限码不合法", errors);
        }
    }

    private String toJson(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception ex) {
            throw new IllegalStateException("permissions 序列化失败", ex);
        }
    }

    private MerchantDtos.StaffVO toVo(StaffEntity staff) {
        return new MerchantDtos.StaffVO(String.valueOf(staff.getId()), staff.getUsername(), staff.getRole(),
                staff.getPermissions() == null ? List.of() : staff.getPermissions(), staff.getStatus(),
                staff.getCreatedAt());
    }

    /** ETag 只由可写配置字段生成：role + 排序后的 permissions + status。 */
    static String etagFor(StaffEntity staff) {
        String permissions = String.join(",", new TreeSet<>(
                staff.getPermissions() == null ? List.of() : staff.getPermissions()));
        return Etags.of(staff.getRole(), permissions, staff.getStatus());
    }
}
