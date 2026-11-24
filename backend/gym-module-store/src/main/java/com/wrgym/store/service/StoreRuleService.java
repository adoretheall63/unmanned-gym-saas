package com.wrgym.store.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.audit.AuditWriter;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.security.PermissionCodes;
import com.wrgym.common.time.TimeZones;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.common.web.Etags;
import com.wrgym.common.web.Ids;
import com.wrgym.security.auth.SecurityUtils;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.dto.StoreRuleDtos;
import com.wrgym.store.entity.StoreEntity;
import com.wrgym.store.entity.StoreSlotEntity;
import com.wrgym.store.mapper.StoreMapper;
import com.wrgym.store.mapper.StoreOpsQueryMapper;
import com.wrgym.store.mapper.StoreSlotMapper;
import com.wrgym.store.support.StoreSupport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店营业与计费规则（docs/接口文档.md §4.9–§4.12）。
 *
 * <ul>
 *   <li>4.9 预览只演算不落库；previewHash = 方案 + 当前承诺量摘要，不冒充授权凭据；</li>
 *   <li>4.10 应用规则：锁内重算预览并比对 previewHash（过期 409 PREVIEW_STALE），
 *       有冲突 409 RULE_CONFLICT（带订单 ID），容量不得低于已有承诺；只改未售未来时段；</li>
 *   <li>4.11 停止/停用：DISABLED 前检查未结束到店、未履约订单、未决设备命令；</li>
 *   <li>4.12 准入暂停/恢复：恢复前当前读检查估计人数与容量，人数未知/超容量不允许恢复。</li>
 * </ul>
 */
@Service
public class StoreRuleService {

    private final StoreMapper storeMapper;
    private final StoreSlotMapper storeSlotMapper;
    private final StoreOpsQueryMapper storeOpsQueryMapper;
    private final SlotGenerationService slotGenerationService;
    private final AuditWriter auditWriter;
    private final IdempotencyService idempotencyService;

    public StoreRuleService(StoreMapper storeMapper, StoreSlotMapper storeSlotMapper,
                            StoreOpsQueryMapper storeOpsQueryMapper,
                            SlotGenerationService slotGenerationService,
                            AuditWriter auditWriter, IdempotencyService idempotencyService) {
        this.storeMapper = storeMapper;
        this.storeSlotMapper = storeSlotMapper;
        this.storeOpsQueryMapper = storeOpsQueryMapper;
        this.slotGenerationService = slotGenerationService;
        this.auditWriter = auditWriter;
        this.idempotencyService = idempotencyService;
    }

    // ---------- 4.9 预览 ----------

    public StoreRuleDtos.RulePreviewResult preview(String storeIdRaw, StoreRuleDtos.RulePreviewRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        StoreEntity store = storeMapper.selectTenant(storeId, merchantId);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        validatePlan(request.openTime(), request.closeTime(), request.is24h(), request.endsNextDay());
        StoreRuleDtos.RulePlan plan = planOf(request);
        return computePreview(store, plan, UtcTimes.now());
    }

    // ---------- 4.10 应用规则 ----------

    public StoreDtos.AdminStoreVo applyRules(String storeIdRaw, String ifMatch, String idempotencyKey,
                                             StoreRuleDtos.ApplyRulesRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        validatePlan(request.openTime(), request.closeTime(), request.is24h(), request.endsNextDay());
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = String.join("\n", storeId + "", String.valueOf(request.openTime()),
                String.valueOf(request.closeTime()), String.valueOf(request.is24h()),
                String.valueOf(request.endsNextDay()), String.valueOf(request.capacity()),
                String.valueOf(request.slotPriceCent()), String.valueOf(request.overtimePriceCent()),
                String.valueOf(request.overtimeCapCent()), request.previewHash(), request.reason());
        return idempotencyService.execute("admin.store.rules", key, requestBody,
                StoreDtos.AdminStoreVo.class, () -> {
                    StoreEntity locked = storeMapper.selectTenantForUpdate(storeId, merchantId);
                    if (locked == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, StoreSupport.etagFor(locked));
                    StoreRuleDtos.RulePlan plan = new StoreRuleDtos.RulePlan(locked.getName(),
                            locked.getAddress(), StoreSupport.decimalText(locked.getLatitude()),
                            StoreSupport.decimalText(locked.getLongitude()), locked.getDescription(),
                            locked.getImages(), request.openTime(), request.closeTime(),
                            request.is24h(), request.endsNextDay(), request.capacity(),
                            request.slotPriceCent(), request.overtimePriceCent(), request.overtimeCapCent());
                    LocalDateTime now = UtcTimes.now();
                    StoreRuleDtos.RulePreviewResult preview = computePreview(locked, plan, now);
                    if (!preview.previewHash().equals(request.previewHash())) {
                        throw new BizException(ErrorCode.PREVIEW_STALE);
                    }
                    if (!preview.conflicts().isEmpty()) {
                        List<FieldErrorItem> errors = preview.conflicts().stream()
                                .map(c -> new FieldErrorItem("conflicts", c.orderId() + ":" + c.reason()))
                                .toList();
                        throw new BizException(ErrorCode.RULE_CONFLICT, "规则与已有承诺冲突，请先逐单沟通处理", errors);
                    }
                    LambdaUpdateWrapper<StoreEntity> update = new LambdaUpdateWrapper<StoreEntity>()
                            .eq(StoreEntity::getId, storeId)
                            .eq(StoreEntity::getMerchantId, merchantId)
                            .set(StoreEntity::getOpenTime, request.openTime())
                            .set(StoreEntity::getCloseTime, request.closeTime())
                            .set(StoreEntity::getIs24h, request.is24h())
                            .set(StoreEntity::getEndsNextDay, request.endsNextDay())
                            .set(StoreEntity::getCapacity, request.capacity())
                            .set(StoreEntity::getSlotPriceCent, request.slotPriceCent())
                            .set(StoreEntity::getOvertimePriceCent, request.overtimePriceCent())
                            .set(StoreEntity::getOvertimeCapCent, request.overtimeCapCent())
                            .set(StoreEntity::getUpdatedAt, now);
                    storeMapper.update(null, update);
                    // 只更新未售未来时段；已售时段与订单规则快照保持不变
                    storeSlotMapper.updateUnsoldRules(merchantId, storeId, now,
                            request.capacity(), request.slotPriceCent());
                    // 按新规则补齐未来缺失的完整小时段
                    slotGenerationService.generate(storeMapper.selectById(storeId), now);
                    auditWriter.record(merchantId, "STORE_RULES_APPLIED", "STORE", storeId, adminId,
                            request.reason(), Map.of("before", ruleSnapshot(locked),
                                    "after", ruleSnapshot(request), "affectedUnsoldSlots",
                                    preview.affectedUnsoldSlots()));
                    return StoreSupport.toAdminVo(storeMapper.selectById(storeId));
                });
    }

    // ---------- 4.11 营业状态 ----------

    public StoreDtos.AdminStoreVo updateStatus(String storeIdRaw, String ifMatch, String idempotencyKey,
                                               StoreRuleDtos.StoreStatusRequest request) {
        SecurityUtils.requireAdminRole();
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = storeId + "\n" + request.status() + "\n" + request.reason();
        return idempotencyService.execute("admin.store.status", key, requestBody,
                StoreDtos.AdminStoreVo.class, () -> {
                    StoreEntity locked = storeMapper.selectTenantForUpdate(storeId, merchantId);
                    if (locked == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, StoreSupport.etagFor(locked));
                    if ("DISABLED".equals(request.status())) {
                        LocalDateTime now = UtcTimes.now();
                        long visits = storeOpsQueryMapper.countUnfinishedVisits(storeId);
                        long orders = storeOpsQueryMapper.countUnfulfilledOrders(storeId, now);
                        long operations = storeOpsQueryMapper.countPendingOperations(storeId);
                        if (visits > 0 || orders > 0 || operations > 0) {
                            throw new BizException(ErrorCode.STATE_CONFLICT,
                                    "仍有未结束到店/" + visits + "、未履约订单/" + orders
                                            + "、未决设备命令/" + operations + "，不能停用");
                        }
                    }
                    storeMapper.update(null, new LambdaUpdateWrapper<StoreEntity>()
                            .eq(StoreEntity::getId, storeId)
                            .eq(StoreEntity::getMerchantId, merchantId)
                            .set(StoreEntity::getStatus, request.status())
                            .set(StoreEntity::getUpdatedAt, UtcTimes.now()));
                    auditWriter.record(merchantId, "STORE_STATUS_CHANGED", "STORE", storeId, adminId,
                            request.reason(), Map.of("before", locked.getStatus(), "after", request.status()));
                    return StoreSupport.toAdminVo(storeMapper.selectById(storeId));
                });
    }

    // ---------- 4.12 准入暂停/恢复 ----------

    public StoreDtos.AdminStoreVo updateAdmission(String storeIdRaw, String ifMatch, String idempotencyKey,
                                                  StoreRuleDtos.AdmissionRequest request) {
        SecurityUtils.requirePermission(PermissionCodes.STORE_CONTROL);
        long merchantId = SecurityUtils.currentMerchantId();
        long adminId = SecurityUtils.currentAdminId();
        long storeId = Ids.parse(storeIdRaw, "storeId");
        String key = idempotencyService.requireKey(idempotencyKey);
        String requestBody = storeId + "\n" + request.paused() + "\n" + request.reason();
        return idempotencyService.execute("admin.store.admission", key, requestBody,
                StoreDtos.AdminStoreVo.class, () -> {
                    StoreEntity locked = storeMapper.selectTenantForUpdate(storeId, merchantId);
                    if (locked == null) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    Etags.verify(ifMatch, StoreSupport.etagFor(locked));
                    if (!request.paused()) {
                        // 恢复自动准入：当前读检查人数与容量；人数未知/超容量不允许恢复
                        if (!"OPEN".equals(locked.getStatus())) {
                            throw new BizException(ErrorCode.STATE_CONFLICT, "门店未处于营业状态，不能恢复准入");
                        }
                        long inStore = storeOpsQueryMapper.countUnfinishedVisits(storeId);
                        int capacity = locked.getCapacity() == null ? 0 : locked.getCapacity();
                        if (capacity <= 0 || inStore >= capacity) {
                            throw new BizException(ErrorCode.OCCUPANCY_UNKNOWN,
                                    "当前在场人数无法确认容量安全（在场 " + inStore + " / 容量 " + capacity + "）");
                        }
                    }
                    storeMapper.update(null, new LambdaUpdateWrapper<StoreEntity>()
                            .eq(StoreEntity::getId, storeId)
                            .eq(StoreEntity::getMerchantId, merchantId)
                            .set(StoreEntity::getAdmissionPaused, request.paused())
                            .set(StoreEntity::getPauseReason, request.paused() ? request.reason() : null)
                            .set(StoreEntity::getUpdatedAt, UtcTimes.now()));
                    auditWriter.record(merchantId, "STORE_ADMISSION_CHANGED", "STORE", storeId, adminId,
                            request.reason(), Map.of("beforePaused", locked.getAdmissionPaused(),
                                    "afterPaused", request.paused()));
                    return StoreSupport.toAdminVo(storeMapper.selectById(storeId));
                });
    }

    // ---------- 内部：预览计算 ----------

    private StoreRuleDtos.RulePreviewResult computePreview(StoreEntity store, StoreRuleDtos.RulePlan plan,
                                                           LocalDateTime now) {
        int maxReserved = storeSlotMapper.maxFutureReserved(store.getMerchantId(), store.getId(), now);
        List<StoreSlotEntity> committed = storeSlotMapper.listFutureCommitted(store.getMerchantId(), store.getId(), now);
        List<StoreSlotEntity> unsold = storeSlotMapper.listFutureUnsold(store.getMerchantId(), store.getId(), now);

        boolean capacityBelowCommitted = plan.capacity() < maxReserved;
        boolean scheduleConflict = committed.stream().anyMatch(slot ->
                !coveredByPlan(plan, slot.getStartAt(), slot.getEndAt())
                        || plan.capacity() < (slot.getReservedCount() == null ? 0 : slot.getReservedCount()));
        List<StoreRuleDtos.RuleConflict> conflicts = new ArrayList<>();
        if (capacityBelowCommitted || scheduleConflict) {
            String reason = capacityBelowCommitted ? "CAPACITY_BELOW_COMMITTED" : "SCHEDULE_CHANGED";
            List<Long> orderIds = storeOpsQueryMapper.listUnfulfilledOrderIds(store.getId(), now);
            if (orderIds.isEmpty()) {
                conflicts.add(new StoreRuleDtos.RuleConflict(null, reason));
            } else {
                orderIds.forEach(id -> conflicts.add(new StoreRuleDtos.RuleConflict(String.valueOf(id), reason)));
            }
        }

        int affectedUnsold = 0;
        for (StoreSlotEntity slot : unsold) {
            long slotPrice = slot.getPriceCent() == null ? 0 : slot.getPriceCent();
            int slotCapacity = slot.getCapacity() == null ? 0 : slot.getCapacity();
            if (slotCapacity != plan.capacity() || slotPrice != plan.slotPriceCent()
                    || !coveredByPlan(plan, slot.getStartAt(), slot.getEndAt())) {
                affectedUnsold++;
            }
        }
        int uncoveredMinutes = slotGenerationService.windowMinutes(plan.openTime(), plan.closeTime(),
                Boolean.TRUE.equals(plan.is24h()), Boolean.TRUE.equals(plan.endsNextDay())) % StoreSupport.SLOT_MINUTES;
        String hash = previewHash(plan, store, committed, maxReserved);
        return new StoreRuleDtos.RulePreviewResult(String.valueOf(store.getId()), hash, affectedUnsold,
                conflicts, uncoveredMinutes, conflicts.isEmpty());
    }

    private boolean coveredByPlan(StoreRuleDtos.RulePlan plan, LocalDateTime startUtc, LocalDateTime endUtc) {
        LocalDateTime start = TimeZones.utcToBusiness(startUtc);
        LocalDateTime end = TimeZones.utcToBusiness(endUtc);
        if (Boolean.TRUE.equals(plan.is24h())) {
            return Duration.between(start, end).toMinutes() > 0;
        }
        LocalDateTime windowStart = LocalDateTime.of(start.toLocalDate(), plan.openTime());
        LocalDateTime windowEnd;
        if (Boolean.TRUE.equals(plan.endsNextDay())) {
            windowEnd = LocalDateTime.of(start.toLocalDate().plusDays(1), plan.closeTime());
        } else {
            windowEnd = LocalDateTime.of(start.toLocalDate(), plan.closeTime());
        }
        return !start.isBefore(windowStart) && !end.isAfter(windowEnd);
    }

    private String previewHash(StoreRuleDtos.RulePlan plan, StoreEntity store,
                               List<StoreSlotEntity> committed, int maxReserved) {
        String commitments = committed.stream()
                .map(slot -> slot.getId() + ":" + slot.getStartAt() + ":" + slot.getEndAt() + ":"
                        + slot.getReservedCount())
                .collect(Collectors.joining(","));
        return Etags.of(plan.openTime(), plan.closeTime(), plan.is24h(), plan.endsNextDay(),
                plan.capacity(), plan.slotPriceCent(), plan.overtimePriceCent(), plan.overtimeCapCent(),
                maxReserved, commitments, store.getMerchantId(), store.getId());
    }

    private void validatePlan(LocalTime openTime, LocalTime closeTime, Boolean is24h, Boolean endsNextDay) {
        StoreSupport.validateOpeningRules(openTime, closeTime, Boolean.TRUE.equals(is24h),
                Boolean.TRUE.equals(endsNextDay));
    }

    private StoreRuleDtos.RulePlan planOf(StoreRuleDtos.RulePreviewRequest request) {
        return new StoreRuleDtos.RulePlan(request.name(), request.address(), request.latitude(),
                request.longitude(), request.description(), request.images(), request.openTime(),
                request.closeTime(), request.is24h(), request.endsNextDay(), request.capacity(),
                request.slotPriceCent(), request.overtimePriceCent(), request.overtimeCapCent());
    }

    private Map<String, Object> ruleSnapshot(StoreEntity store) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("openTime", StoreSupport.timeText(store.getOpenTime()));
        snapshot.put("closeTime", StoreSupport.timeText(store.getCloseTime()));
        snapshot.put("is24h", store.getIs24h());
        snapshot.put("endsNextDay", store.getEndsNextDay());
        snapshot.put("capacity", store.getCapacity());
        snapshot.put("slotPriceCent", store.getSlotPriceCent());
        snapshot.put("overtimePriceCent", store.getOvertimePriceCent());
        snapshot.put("overtimeCapCent", store.getOvertimeCapCent());
        return snapshot;
    }

    private Map<String, Object> ruleSnapshot(StoreRuleDtos.ApplyRulesRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("openTime", StoreSupport.timeText(request.openTime()));
        snapshot.put("closeTime", StoreSupport.timeText(request.closeTime()));
        snapshot.put("is24h", request.is24h());
        snapshot.put("endsNextDay", request.endsNextDay());
        snapshot.put("capacity", request.capacity());
        snapshot.put("slotPriceCent", request.slotPriceCent());
        snapshot.put("overtimePriceCent", request.overtimePriceCent());
        snapshot.put("overtimeCapCent", request.overtimeCapCent());
        return snapshot;
    }
}
