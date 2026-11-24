package com.wrgym.store.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.audit.AuditWriter;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.common.web.Etags;
import com.wrgym.common.web.Ids;
import com.wrgym.security.auth.SecurityUtils;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.dto.StoreRuleDtos;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.entity.StoreSlotEntity;
import com.wrgym.store.mapper.StoreMapper;
import com.wrgym.store.mapper.StoreSlotMapper;
import com.wrgym.store.support.SlotViewSupport;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 时段生成与单时段调整（docs/接口文档.md §4.14、§4.15）。
 *
 * <p>4.14：与定时生成共用幂等业务服务，(storeId,startAt,endAt) 唯一防重；只补未来完整小时段。
 * <p>4.15：幂等 + If-Match（取后台时段列表项 etag）；只允许修改**尚未开始且 reservedCount=0** 的时段，
 * 已承诺返回 409 SLOT_COMMITTED；摘要只对可写配置计算，不用 reservedCount 冒充配置版本。
 */
@Service
public class StoreSlotAdminService {

    private final StoreMapper storeMapper;
    private final StoreSlotMapper storeSlotMapper;
    private final SlotGenerationService slotGenerationService;
    private final AuditWriter auditWriter;
    private final IdempotencyService idempotencyService;

    public StoreSlotAdminService(StoreMapper storeMapper, StoreSlotMapper storeSlotMapper,
                                 SlotGenerationService slotGenerationService, AuditWriter auditWriter,
                                 IdempotencyService idempotencyService) {
        this.storeMapper = storeMapper;
        this.storeSlotMapper = storeSlotMapper;
        this.slotGenerationService = slotGenerationService;
        this.auditWriter = auditWriter;
        this.idempotencyService = idempotencyService;
    }

    /** 4.14 补齐未来七天时段；POST 返回 200 且 data 仅 accepted。 */
    public StoreRuleDtos.Accepted generate(String storeIdRaw, String idempotencyKey, String reason) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "reason 必填（1–500 字符）");
        }
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = storeId + "\n" + reason;
        return idempotencyService.execute("admin.store.slot-generation", key, requestBody,
                StoreRuleDtos.Accepted.class, () -> {
                    StoreEntity store = storeMapper.selectTenant(storeId, merchantId);
                    if (store == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    int inserted = slotGenerationService.generate(store, UtcTimes.now());
                    auditWriter.record(merchantId, "STORE_SLOTS_GENERATED", "STORE", storeId, adminId,
                            reason, Map.of("inserted", inserted));
                    return new StoreRuleDtos.Accepted(true);
                });
    }

    /** 4.15 调整未售时段。 */
    public StoreDtos.AdminSlotItem adjustSlot(String storeIdRaw, String slotIdRaw, String ifMatch,
                                              String idempotencyKey, StoreRuleDtos.SlotAdjustRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        long slotId = Ids.parse(slotIdRaw, "slotId");
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = String.join("\n", storeId + "", slotId + "", String.valueOf(request.capacity()),
                String.valueOf(request.priceCent()), request.status(), request.reason());
        return idempotencyService.execute("admin.store.slot-adjust", key, requestBody,
                StoreDtos.AdminSlotItem.class, () -> {
                    StoreSlotEntity slot = storeSlotMapper.selectTenantForUpdate(slotId, storeId, merchantId);
                    if (slot == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, SlotViewSupport.etagFor(slot));
                    var now = UtcTimes.now();
                    if (slot.getStartAt() == null || !slot.getStartAt().isAfter(now)
                            || (slot.getReservedCount() != null && slot.getReservedCount() > 0)) {
                        throw new BizException(ErrorCode.SLOT_COMMITTED);
                    }
                    storeSlotMapper.update(null, new LambdaUpdateWrapper<StoreSlotEntity>()
                            .eq(StoreSlotEntity::getId, slotId)
                            .eq(StoreSlotEntity::getStoreId, storeId)
                            .eq(StoreSlotEntity::getMerchantId, merchantId)
                            .set(StoreSlotEntity::getCapacity, request.capacity())
                            .set(StoreSlotEntity::getPriceCent, request.priceCent())
                            .set(StoreSlotEntity::getStatus, request.status())
                            .set(StoreSlotEntity::getUpdatedAt, now));
                    auditWriter.record(merchantId, "STORE_SLOT_ADJUSTED", "STORE", storeId, adminId,
                            request.reason(), Map.of("slotId", slotId,
                                    "beforeCapacity", slot.getCapacity(), "afterCapacity", request.capacity(),
                                    "beforePriceCent", slot.getPriceCent(), "afterPriceCent", request.priceCent(),
                                    "beforeStatus", slot.getStatus(), "afterStatus", request.status()));
                    StoreSlotEntity updated = storeSlotMapper.selectById(slotId);
                    return new StoreDtos.AdminSlotItem(String.valueOf(updated.getId()),
                            String.valueOf(updated.getStoreId()), updated.getStartAt(), updated.getEndAt(),
                            SlotViewSupport.capacity(updated), SlotViewSupport.remaining(updated),
                            updated.getPriceCent(), updated.getStatus(),
                            SlotViewSupport.bookable(updated, now),
                            SlotViewSupport.unavailableReason(updated, now),
                            SlotViewSupport.etagFor(updated));
                });
    }
}
