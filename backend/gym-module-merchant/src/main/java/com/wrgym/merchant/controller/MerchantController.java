package com.wrgym.merchant.controller;

import com.wrgym.common.api.R;
import com.wrgym.merchant.dto.MerchantDtos;
import com.wrgym.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家资料（docs/接口文档.md §3.1 / §3.2）。
 *
 * <p>3.1 返回 ETag；3.2 必须带 Idempotency-Key 与 If-Match（缺失 428、不匹配 412）。
 */
@RestController
@RequestMapping("/api/v1/admin/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ResponseEntity<R<MerchantDtos.MerchantVO>> current() {
        MerchantService.MerchantView view = merchantService.current();
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .body(R.ok(view.value()));
    }

    @PatchMapping
    public ResponseEntity<R<MerchantDtos.MerchantVO>> updateName(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MerchantDtos.UpdateMerchantRequest request) {
        MerchantService.MerchantView view = merchantService.updateName(ifMatch, idempotencyKey, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .body(R.ok(view.value()));
    }
}
