package com.hiking.gymsaas.account.service;

import com.hiking.gymsaas.account.model.dto.AuthRequests;
import com.hiking.gymsaas.account.model.vo.AuthViews;
import com.hiking.gymsaas.common.idempotency.IdempotencyService;

public interface MerchantService {
    AuthViews.Merchant self();
    IdempotencyService.Outcome<AuthViews.Merchant> rename(AuthRequests.MerchantName input, String key, String ifMatch);
}
