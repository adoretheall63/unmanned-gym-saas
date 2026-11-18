package com.hiking.gymsaas.operation.service;

import com.hiking.gymsaas.common.page.PageResult;
import com.hiking.gymsaas.common.security.Identity;
import com.hiking.gymsaas.operation.model.dto.AuditQuery;
import com.hiking.gymsaas.operation.model.vo.AuditView;
import java.util.Map;

public interface AuditService {
    void append(long verifiedMerchantId, String eventType, String targetType, long targetId, String reason, Map<String, ?> detail);
    void login(Identity verifiedIdentity);
    PageResult<AuditView> list(AuditQuery query);
    AuditView detail(long id);
}
