package com.hiking.gymsaas.account.controller;

import com.hiking.gymsaas.account.model.dto.AuthRequests;
import com.hiking.gymsaas.account.model.vo.AuthViews;
import com.hiking.gymsaas.account.service.MerchantService;
import com.hiking.gymsaas.common.web.ApiResponse;
import com.hiking.gymsaas.common.web.ConfigurationEtags;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/admin/merchant")
public class MerchantController {
    private final MerchantService merchants;
    public MerchantController(MerchantService merchants) { this.merchants = merchants; }

    @GetMapping
    public ResponseEntity<ApiResponse<AuthViews.Merchant>> self() {
        var result = merchants.self();
        return ResponseEntity.ok().eTag(ConfigurationEtags.of(result.name())).body(ApiResponse.ok(result));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<AuthViews.Merchant>> rename(@Valid @RequestBody AuthRequests.MerchantName input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        var result = merchants.rename(input, key, ifMatch);
        return ResponseEntity.status(result.status()).eTag(ConfigurationEtags.of(result.data().name()))
                .body(ApiResponse.ok(result.data()));
    }
}
