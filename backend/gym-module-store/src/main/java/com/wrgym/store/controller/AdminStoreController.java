package com.wrgym.store.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.api.R;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.dto.StoreRuleDtos;
import com.wrgym.store.service.StoreAdminService;
import com.wrgym.store.service.StoreImageService;
import com.wrgym.store.service.StoreQueryService;
import com.wrgym.store.service.StoreRuleService;
import com.wrgym.store.service.StoreSlotAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台门店管理（docs/接口文档.md §4.4–§4.15）。
 *
 * <p>查询类（4.4/4.5/4.13）需要 STORE_READ；写类需要 ADMIN，4.12 例外为 ADMIN 或 STORE_CONTROL。
 * <p>4.5/4.7/4.10/4.11/4.12 返回 ETag；4.7/4.10/4.11/4.12/4.15 必须带 If-Match（缺失 428、不匹配 412）。
 */
@RestController
@RequestMapping("/api/v1/admin/stores")
public class AdminStoreController {

    private final StoreAdminService storeAdminService;
    private final StoreQueryService storeQueryService;
    private final StoreImageService storeImageService;
    private final StoreRuleService storeRuleService;
    private final StoreSlotAdminService storeSlotAdminService;

    public AdminStoreController(StoreAdminService storeAdminService, StoreQueryService storeQueryService,
                                StoreImageService storeImageService, StoreRuleService storeRuleService,
                                StoreSlotAdminService storeSlotAdminService) {
        this.storeAdminService = storeAdminService;
        this.storeQueryService = storeQueryService;
        this.storeImageService = storeImageService;
        this.storeRuleService = storeRuleService;
        this.storeSlotAdminService = storeSlotAdminService;
    }

    // ---------- 4.4 / 4.5 / 4.6 / 4.7 ----------

    @GetMapping
    public R<PageResult<StoreDtos.AdminStoreVo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageQuery query = new PageQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        return R.ok(storeAdminService.list(query, status));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> detail(@PathVariable String storeId) {
        StoreDtos.AdminStoreVo vo = storeAdminService.detail(storeId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, vo.etag()).body(R.ok(vo));
    }

    @PostMapping
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StoreDtos.CreateStoreRequest request) {
        StoreDtos.AdminStoreVo created = storeAdminService.create(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(created));
    }

    @PatchMapping("/{storeId}")
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> updateProfile(
            @PathVariable String storeId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode body) {
        StoreDtos.AdminStoreVo updated = storeAdminService.updateProfile(storeId, ifMatch, idempotencyKey, body);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, updated.etag()).body(R.ok(updated));
    }

    // ---------- 4.8 图片上传 ----------

    @PostMapping("/{storeId}/images")
    public ResponseEntity<R<StoreRuleDtos.UploadResult>> uploadImage(
            @PathVariable String storeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file) {
        StoreRuleDtos.UploadResult result = storeImageService.upload(storeId, idempotencyKey, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(result));
    }

    // ---------- 4.9 规则预览 ----------

    @PostMapping("/{storeId}/rule-preview")
    public R<StoreRuleDtos.RulePreviewResult> previewRules(
            @PathVariable String storeId,
            @Valid @RequestBody StoreRuleDtos.RulePreviewRequest request) {
        return R.ok(storeRuleService.preview(storeId, request));
    }

    // ---------- 4.10 应用规则 ----------

    @PutMapping("/{storeId}/rules")
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> applyRules(
            @PathVariable String storeId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StoreRuleDtos.ApplyRulesRequest request) {
        StoreDtos.AdminStoreVo updated = storeRuleService.applyRules(storeId, ifMatch, idempotencyKey, request);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, updated.etag()).body(R.ok(updated));
    }

    // ---------- 4.11 营业状态 ----------

    @PutMapping("/{storeId}/status")
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> updateStatus(
            @PathVariable String storeId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StoreRuleDtos.StoreStatusRequest request) {
        StoreDtos.AdminStoreVo updated = storeRuleService.updateStatus(storeId, ifMatch, idempotencyKey, request);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, updated.etag()).body(R.ok(updated));
    }

    // ---------- 4.12 准入暂停/恢复 ----------

    @PutMapping("/{storeId}/admission")
    public ResponseEntity<R<StoreDtos.AdminStoreVo>> updateAdmission(
            @PathVariable String storeId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StoreRuleDtos.AdmissionRequest request) {
        StoreDtos.AdminStoreVo updated = storeRuleService.updateAdmission(storeId, ifMatch, idempotencyKey, request);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, updated.etag()).body(R.ok(updated));
    }

    // ---------- 4.13 后台时段列表 ----------

    @GetMapping("/{storeId}/slots")
    public R<StoreDtos.AdminSlotList> slots(@PathVariable String storeId,
                                            @RequestParam String date) {
        return R.ok(storeQueryService.adminSlots(storeId, date));
    }

    // ---------- 4.14 补齐未来七天时段 ----------

    @PostMapping("/{storeId}/slot-generation")
    public R<StoreRuleDtos.Accepted> generateSlots(
            @PathVariable String storeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) JsonNode body) {
        String reason = body == null || !body.hasNonNull("reason") ? null : body.get("reason").asText();
        return R.ok(storeSlotAdminService.generate(storeId, idempotencyKey, reason));
    }

    // ---------- 4.15 调整未售时段 ----------

    @PatchMapping("/{storeId}/slots/{slotId}")
    public ResponseEntity<R<StoreDtos.AdminSlotItem>> adjustSlot(
            @PathVariable String storeId,
            @PathVariable String slotId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StoreRuleDtos.SlotAdjustRequest request) {
        StoreDtos.AdminSlotItem updated = storeSlotAdminService.adjustSlot(storeId, slotId, ifMatch,
                idempotencyKey, request);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, updated.etag()).body(R.ok(updated));
    }
}
