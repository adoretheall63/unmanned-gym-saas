package com.wrgym.store.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.PageResult;
import com.wrgym.common.api.R;
import com.wrgym.store.dto.StoreDtos;
import com.wrgym.store.service.StoreAdminService;
import com.wrgym.store.service.StoreQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台门店管理（docs/接口文档.md §4.4–§4.7、§4.13）。
 *
 * <p>4.4/4.5/4.13 需要 STORE_READ；4.6/4.7 需要 ADMIN。
 * <p>4.5/4.7 返回 ETag；4.7 必须带 Idempotency-Key 与 If-Match。
 */
@RestController
@RequestMapping("/api/v1/admin/stores")
public class AdminStoreController {

    private final StoreAdminService storeAdminService;
    private final StoreQueryService storeQueryService;

    public AdminStoreController(StoreAdminService storeAdminService, StoreQueryService storeQueryService) {
        this.storeAdminService = storeAdminService;
        this.storeQueryService = storeQueryService;
    }

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
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, vo.etag())
                .body(R.ok(vo));
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
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, updated.etag())
                .body(R.ok(updated));
    }

    @GetMapping("/{storeId}/slots")
    public R<StoreDtos.AdminSlotList> slots(@PathVariable String storeId,
                                            @RequestParam String date) {
        return R.ok(storeQueryService.adminSlots(storeId, date));
    }
}
