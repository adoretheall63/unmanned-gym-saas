package com.wrgym.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrgym.common.audit.mapper.BusinessEventMapper;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.common.web.RequestContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计写入器（跨模块共享能力）。
 *
 * <p>用于"变更必须留痕"的操作：员工/商家资料、权限、状态、密码重置等（docs/接口文档.md §3）。
 * 每次写入一行 {@code business_event}（只追加，不更新/删除）；调用方必须在自己的业务事务内调用，
 * 保证业务结果与审计同时提交。
 */
@Service
public class AuditWriter {

    private final BusinessEventMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditWriter(BusinessEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * @param merchantId 商家（必须来自 JWT，不来自请求体）
     * @param eventType  业务事件类型，如 STAFF_ROLE_CHANGED
     * @param targetType 目标类型，如 ADMIN / MERCHANT
     * @param targetId   目标ID
     * @param adminId    操作员工ID（人工操作必填）
     * @param reason     人工操作原因（必填，契约要求 1–500 字符）
     * @param detail     脱敏前后状态等证据，必须可 JSON 序列化
     */
    public void record(long merchantId, String eventType, String targetType, long targetId,
                       long adminId, String reason, Map<String, Object> detail) {
        BusinessEventEntity event = new BusinessEventEntity();
        event.setMerchantId(merchantId);
        event.setEventType(eventType);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setAdminId(adminId);
        event.setActorType("ADMIN");
        event.setRequestId(RequestContext.currentRequestId());
        event.setReason(reason);
        event.setDetail(toJson(detail));
        event.setOccurredAt(UtcTimes.now());
        event.setCreatedAt(UtcTimes.now());
        mapper.insert(event);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (Exception ex) {
            throw new IllegalStateException("审计 detail 序列化失败", ex);
        }
    }
}
