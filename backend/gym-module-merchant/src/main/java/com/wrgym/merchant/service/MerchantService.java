package com.wrgym.merchant.service;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.audit.AuditWriter;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.web.Etags;
import com.wrgym.merchant.dto.MerchantDtos;
import com.wrgym.merchant.entity.MerchantEntity;
import com.wrgym.merchant.mapper.MerchantProfileMapper;
import com.wrgym.security.auth.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 商家资料（docs/接口文档.md §3.1 / §3.2）。
 *
 * <p>3.1 任意已认证后台身份可读；3.2 仅 ADMIN，且必须带 Idempotency-Key 与 If-Match。
 * ETag 由可写配置字段（name）生成；改名在行锁内重算并再次比对 ETag，然后写审计。
 */
@Service
public class MerchantService {

    private final MerchantProfileMapper merchantMapper;
    private final AuditWriter auditWriter;
    private final IdempotencyService idempotencyService;

    public MerchantService(MerchantProfileMapper merchantMapper, AuditWriter auditWriter,
                           IdempotencyService idempotencyService) {
        this.merchantMapper = merchantMapper;
        this.auditWriter = auditWriter;
        this.idempotencyService = idempotencyService;
    }

    public record MerchantView(MerchantDtos.MerchantVO value, String etag) {
    }

    /** 3.1 读取本商家信息（商家由 JWT 确定）。 */
    public MerchantView current() {
        long merchantId = SecurityUtils.currentMerchantId();
        MerchantEntity merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toView(merchant);
    }

    /** 3.2 修改本商家显示名称：幂等 + ETag + 审计，不能停用商家或改变归属。 */
    public MerchantView updateName(String ifMatch, String idempotencyKey, MerchantDtos.UpdateMerchantRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = request.name() + "\n" + request.reason();
        MerchantDtos.MerchantVO updated = idempotencyService.execute("admin.merchant.name", key, requestBody,
                MerchantDtos.MerchantVO.class, () -> {
                    MerchantEntity locked = merchantMapper.selectForUpdate(merchantId);
                    if (locked == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    // 锁内用当前字段重算 ETag 再比对（不能只校验请求到达时的值）
                    Etags.verify(ifMatch, etagFor(locked));
                    merchantMapper.updateName(merchantId, request.name());
                    auditWriter.record(merchantId, "MERCHANT_NAME_CHANGED", "MERCHANT", merchantId, adminId,
                            request.reason(), Map.of("before", locked.getName(), "after", request.name()));
                    return toMerchantVo(merchantMapper.selectById(merchantId));
                });
        MerchantEntity current = merchantMapper.selectById(merchantId);
        return new MerchantView(updated, etagFor(current));
    }

    private MerchantView toView(MerchantEntity merchant) {
        return new MerchantView(toMerchantVo(merchant), etagFor(merchant));
    }

    private MerchantDtos.MerchantVO toMerchantVo(MerchantEntity merchant) {
        return new MerchantDtos.MerchantVO(String.valueOf(merchant.getId()), merchant.getName(),
                merchant.getStatus());
    }

    /** ETag 只由可写配置字段生成：当前只有展示名。 */
    static String etagFor(MerchantEntity merchant) {
        return Etags.of(merchant.getName());
    }
}
