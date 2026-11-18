package com.hiking.gymsaas.operation.model.vo;

import java.time.OffsetDateTime;

public record AuditView(String id, String eventType, String targetType, String targetId, String actorType,
                        String adminId, String userId, String deviceEventId, String reason, String summary,
                        OffsetDateTime occurredAt) { }
