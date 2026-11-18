package com.hiking.gymsaas.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.page.PageResult;
import com.hiking.gymsaas.common.security.CurrentIdentity;
import com.hiking.gymsaas.common.security.Identity;
import com.hiking.gymsaas.common.util.Ids;
import com.hiking.gymsaas.common.util.JsonSnapshots;
import com.hiking.gymsaas.common.web.RequestIds;
import com.hiking.gymsaas.operation.mapper.AuditMapper;
import com.hiking.gymsaas.operation.model.dto.AuditQuery;
import com.hiking.gymsaas.operation.model.entity.BusinessEvent;
import com.hiking.gymsaas.operation.model.vo.AuditView;
import com.hiking.gymsaas.operation.service.AuditService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class AuditServiceImpl implements AuditService {
    private final AuditMapper mapper;
    private final JsonSnapshots snapshots;
    public AuditServiceImpl(AuditMapper mapper, JsonSnapshots snapshots) { this.mapper = mapper; this.snapshots = snapshots; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(long verifiedMerchantId, String eventType, String targetType, long targetId, String reason, Map<String, ?> detail) {
        var actor = CurrentIdentity.get();
        if (actor.type() == Identity.Type.ADMIN) CurrentIdentity.requireMerchant(verifiedMerchantId);
        else if (mapper.lockCustomerMembership(verifiedMerchantId, actor.id()) == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        appendEvent(actor, verifiedMerchantId, eventType, targetType, targetId, reason, detail);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void login(Identity verifiedIdentity) {
        if (verifiedIdentity.type() != Identity.Type.ADMIN) throw new IllegalArgumentException("Login audit expects a verified admin");
        appendEvent(verifiedIdentity, verifiedIdentity.merchantId(), "ADMIN_LOGIN", "ADMIN", verifiedIdentity.id(), "后台账号登录", Map.of("summary", "后台登录成功"));
    }

    private void appendEvent(Identity actor, long merchantId, String eventType, String targetType, long targetId, String reason, Map<String, ?> detail) {
        if ((actor.type() == Identity.Type.ADMIN && (reason == null || reason.isBlank())) || (reason != null && reason.length() > 500)
                || targetId < 1 || eventType.length() > 64 || targetType.length() > 32) {
            throw new IllegalArgumentException("Invalid server-side audit event");
        }
        var event = new BusinessEvent();
        event.setMerchantId(merchantId);
        if (actor.type() == Identity.Type.ADMIN) event.setAdminId(actor.id());
        else event.setUserId(actor.id());
        event.setActorType(actor.type().name());
        event.setEventType(eventType);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setReason(reason);
        event.setRequestId(RequestIds.current());
        event.setDetail(snapshots.redact(detail));
        event.setOccurredAt(java.time.LocalDateTime.now(ZoneOffset.UTC));
        if (mapper.appendVerifiedIdentity(event) != 1) throw new IllegalStateException("Audit append failed");
    }

    @Override
    public PageResult<AuditView> list(AuditQuery query) {
        if ((query.getTargetType() == null) != (query.getTargetId() == null)
                || (query.getFrom() == null) != (query.getTo() == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var to = query.getTo() == null ? OffsetDateTime.now(ZoneOffset.UTC) : query.getTo();
        var from = query.getFrom() == null ? to.minusDays(1) : query.getFrom();
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var filter = new LambdaQueryWrapper<BusinessEvent>()
                .ge(BusinessEvent::getOccurredAt, from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .lt(BusinessEvent::getOccurredAt, to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .orderByDesc(BusinessEvent::getOccurredAt, BusinessEvent::getId);
        if (query.getTargetType() != null) filter.eq(BusinessEvent::getTargetType, query.getTargetType())
                .eq(BusinessEvent::getTargetId, Ids.parse(query.getTargetId()));
        var page = mapper.selectPage(query.toPage(), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::view).toList());
    }

    @Override
    public AuditView detail(long id) {
        var event = mapper.selectById(id);
        if (event == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return view(event);
    }

    private AuditView view(BusinessEvent event) {
        return new AuditView(event.getId().toString(), event.getEventType(), event.getTargetType(), event.getTargetId().toString(),
                event.getActorType(), string(event.getAdminId()), string(event.getUserId()), string(event.getDeviceEventId()),
                event.getReason(), event.getDetail().path("summary").asText(""), event.getOccurredAt().atOffset(ZoneOffset.UTC));
    }
    private String string(Long id) { return id == null ? null : id.toString(); }
}
