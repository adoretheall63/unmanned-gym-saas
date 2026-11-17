package com.wrgym.common.idempotency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.FieldErrorItem;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.mapper.ApiRequestMapper;
import com.wrgym.common.security.PrincipalRef;
import com.wrgym.common.security.SecurityPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * 幂等写执行器（docs/接口文档.md §1.2、docs/数据库设计.md §5）。
 *
 * <p>契约要求"业务结果和 api_request 在同一本地事务提交"。用法：
 * <pre>{@code
 * return idempotency.execute("app.me.nickname", idempotencyKey, body, ProfileVO.class,
 *         () -> profileService.updateNickname(nickname));
 * }</pre>
 * 执行器开启一个本地事务：插入幂等占位记录 → 执行业务 action → 回写 HTTP 状态与脱敏快照 → 提交。
 * 并发重复提交由唯一键冲突兜底：事务回滚后读取已提交记录，比较 request_hash 决定重放或 409。
 * <p>幂等键缺失/格式非法 → 400 VALIDATION_ERROR（8–64 位 {@code [A-Za-z0-9_-]}）。
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PATTERN = "[A-Za-z0-9_-]{8,64}";

    private final ApiRequestMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(ApiRequestMapper mapper, ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 校验并返回规范化后的幂等键。缺失或非法直接 400。 */
    public String requireKey(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "缺少 Idempotency-Key",
                    List.of(new FieldErrorItem("Idempotency-Key", "写接口必须提供 8–64 位幂等键")));
        }
        String key = headerValue.trim();
        if (!key.matches(KEY_PATTERN)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 格式不合法",
                    List.of(new FieldErrorItem("Idempotency-Key", "只允许字母数字、下划线与短横线，长度 8–64")));
        }
        return key;
    }

    /**
     * @param operation   稳定的操作标识（如 app.me.nickname），同一动作重试必须一致
     * @param key         幂等键（来自 Idempotency-Key 头）
     * @param requestBody 用于计算请求摘要的规范化请求体（可含敏感字段的不可逆摘要，不得原文入库）
     * @param resultType  结果类型，用于重放反序列化
     * @param action      业务动作；必须可安全重放（本执行器保证同一键只执行一次）
     */
    public <T> T execute(String operation, String key, String requestBody, Class<T> resultType,
                         Supplier<T> action) {
        SecurityPrincipal principal = PrincipalRef.current();
        String hash = hash(operation, principal.type(), principal.id(), requestBody);
        ApiRequestEntity existing = find(principal, operation, key);
        if (existing != null) {
            return replay(existing, hash, resultType);
        }
        try {
            return transactionTemplate.execute(status -> {
                ApiRequestEntity row = new ApiRequestEntity();
                row.setPrincipalType(principal.type());
                row.setPrincipalId(principal.id());
                row.setOperation(operation);
                row.setIdempotencyKey(key);
                row.setRequestHash(hash);
                mapper.insert(row);

                T result = action.get();

                row.setHttpStatus(200);
                row.setResourceType(resultType.getSimpleName());
                row.setResultSnapshot(serialize(result));
                mapper.updateById(row);
                return result;
            });
        } catch (DuplicateKeyException duplicate) {
            // 并发同键：让对方事务先提交，再按已提交结果重放
            ApiRequestEntity committed = find(principal, operation, key);
            if (committed == null) {
                throw new BizException(ErrorCode.STATE_CONFLICT);
            }
            log.info("idempotent replay after duplicate key operation={} principal={}:{}",
                    operation, principal.type(), principal.id());
            return replay(committed, hash, resultType);
        }
    }

    private ApiRequestEntity find(SecurityPrincipal principal, String operation, String key) {
        return mapper.selectOne(new LambdaQueryWrapper<ApiRequestEntity>()
                .eq(ApiRequestEntity::getPrincipalType, principal.type())
                .eq(ApiRequestEntity::getPrincipalId, principal.id())
                .eq(ApiRequestEntity::getOperation, operation)
                .eq(ApiRequestEntity::getIdempotencyKey, key));
    }

    private <T> T replay(ApiRequestEntity existing, String hash, Class<T> resultType) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (existing.getResultSnapshot() == null) {
            // 同键请求仍在处理或上次异常中断，不允许重复执行
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        try {
            return objectMapper.readValue(existing.getResultSnapshot(), resultType);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "幂等结果解析失败", ex);
        }
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "幂等快照序列化失败", ex);
        }
    }

    /** 请求摘要：operation + 主体 + 规范化请求体 的 SHA-256（同键不同内容 → 409）。 */
    public static String hash(String operation, String principalType, long principalId, String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = operation + "\n" + principalType + ":" + principalId + "\n"
                    + (requestBody == null ? "" : requestBody);
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
