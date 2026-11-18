package com.hiking.gymsaas.account.service.impl;

import com.hiking.gymsaas.account.mapper.MerchantMapper;
import com.hiking.gymsaas.account.model.dto.AuthRequests;
import com.hiking.gymsaas.account.model.entity.Merchant;
import com.hiking.gymsaas.account.model.vo.AuthViews;
import com.hiking.gymsaas.account.service.MerchantService;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.idempotency.IdempotencyService;
import com.hiking.gymsaas.common.security.CurrentIdentity;
import com.hiking.gymsaas.common.web.ConfigurationEtags;
import com.hiking.gymsaas.operation.service.AuditService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class MerchantServiceImpl implements MerchantService {
    private final MerchantMapper mapper;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public MerchantServiceImpl(MerchantMapper mapper, IdempotencyService idempotency, AuditService audit) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Override
    public AuthViews.Merchant self() {
        var merchant = mapper.selectById(CurrentIdentity.merchantId());
        if (merchant == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return view(merchant);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public IdempotencyService.Outcome<AuthViews.Merchant> rename(AuthRequests.MerchantName input, String key, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED);
        return idempotency.execute("renameMerchant", "PATCH", "/api/v1/admin/merchant", key, input, AuthViews.Merchant.class, () -> {
            var merchant = mapper.lock(CurrentIdentity.merchantId());
            if (merchant == null) throw new BusinessException(ErrorCode.NOT_FOUND);
            ConfigurationEtags.requireMatch(ifMatch, ConfigurationEtags.of(merchant.getName()));
            String previous = merchant.getName();
            if (mapper.rename(merchant.getId(), input.name()) != 1) throw new BusinessException(ErrorCode.STATE_CONFLICT);
            merchant.setName(input.name());
            audit.append(merchant.getId(), "MERCHANT_NAME_CHANGED", "MERCHANT", merchant.getId(), input.reason(),
                    Map.of("summary", "修改商家显示名称", "before", Map.of("name", previous), "after", Map.of("name", input.name())));
            return new IdempotencyService.Outcome<>(200, view(merchant));
        });
    }

    private AuthViews.Merchant view(Merchant merchant) {
        return new AuthViews.Merchant(merchant.getId().toString(), merchant.getName(), merchant.getStatus());
    }
}
