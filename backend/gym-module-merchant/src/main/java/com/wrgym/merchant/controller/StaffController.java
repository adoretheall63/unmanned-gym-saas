package com.wrgym.merchant.controller;

import com.wrgym.common.api.PageResult;
import com.wrgym.common.api.R;
import com.wrgym.merchant.dto.MerchantDtos;
import com.wrgym.merchant.service.StaffService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工管理（docs/接口文档.md §3.3–§3.8）。
 *
 * <p>3.3 列表分页 page/pageSize/total/items；3.4 返回 ETag；3.5 创建返回 201；
 * 3.6/3.7 必须带 Idempotency-Key 与 If-Match；3.8 只返回 accepted。
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public R<PageResult<MerchantDtos.StaffVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return R.ok(staffService.list(new MerchantDtos.StaffListQuery(page, pageSize, status)));
    }

    @GetMapping("/{staffId}")
    public ResponseEntity<R<MerchantDtos.StaffVO>> detail(@PathVariable String staffId) {
        StaffService.StaffView view = staffService.detail(staffId);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .body(R.ok(view.value()));
    }

    @PostMapping
    public ResponseEntity<R<MerchantDtos.StaffVO>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantDtos.CreateStaffRequest request) {
        MerchantDtos.StaffVO created = staffService.create(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(created));
    }

    @PatchMapping("/{staffId}")
    public ResponseEntity<R<MerchantDtos.StaffVO>> updateRole(
            @PathVariable String staffId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantDtos.UpdateStaffRequest request) {
        StaffService.StaffView view = staffService.updateRole(staffId, ifMatch, idempotencyKey, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .body(R.ok(view.value()));
    }

    @PutMapping("/{staffId}/status")
    public ResponseEntity<R<MerchantDtos.StaffVO>> updateStatus(
            @PathVariable String staffId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantDtos.UpdateStaffStatusRequest request) {
        StaffService.StaffView view = staffService.updateStatus(staffId, ifMatch, idempotencyKey, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .body(R.ok(view.value()));
    }

    @PostMapping("/{staffId}/password-reset")
    public R<MerchantDtos.Accepted> resetPassword(
            @PathVariable String staffId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantDtos.ResetStaffPasswordRequest request) {
        return R.ok(staffService.resetPassword(staffId, idempotencyKey, request));
    }
}
