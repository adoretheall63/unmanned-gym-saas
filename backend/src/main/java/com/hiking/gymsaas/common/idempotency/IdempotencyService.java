package com.hiking.gymsaas.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.gymsaas.account.service.IdentityService;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.idempotency.mapper.IdempotencyMapper;
import com.hiking.gymsaas.common.idempotency.model.entity.ApiRequest;
import com.hiking.gymsaas.common.security.CurrentIdentity;
import com.hiking.gymsaas.common.util.Hashes;
import com.hiking.gymsaas.common.util.JsonSnapshots;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!bootstrap")
public class IdempotencyService {
    public record Outcome<T>(int status, T data) { }
    private final IdempotencyMapper mapper;
    private final IdentityService identities;
    private final JsonSnapshots snapshots;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public IdempotencyService(IdempotencyMapper mapper, IdentityService identities, JsonSnapshots snapshots,
                              ObjectMapper json, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.identities = identities;
        this.snapshots = snapshots;
        this.json = json;
        transactions = new TransactionTemplate(manager);
    }

    // The callback must contain database work only: deadlock retries can execute it again.
    public <T> Outcome<T> execute(String operationId, String method, String normalizedPath, String key, Object body,
                                   Class<T> resultType, Supplier<Outcome<T>> work) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (operationId == null || !operationId.matches("[A-Za-z][A-Za-z0-9]{0,47}")
                || !normalizedPath.startsWith("/api/v1/") || normalizedPath.contains("?")) {
            throw new IllegalArgumentException("Use a fixed operationId and normalized resource path");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotency must own the outer business transaction");
        }
        var identity = CurrentIdentity.get();
        var request = new ApiRequest();
        request.setPrincipalType(identity.type().name());
        request.setPrincipalId(identity.id());
        request.setOperation(operationId + ":" + Hashes.sha256(normalizedPath));
        request.setIdempotencyKey(key);
        request.setRequestHash(Hashes.sha256(method + "\n" + normalizedPath + "\n" + snapshots.canonical(body)));
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                try {
                    return transactions.execute(tx -> {
                        try { mapper.claim(request); }
                        catch (DuplicateKeyException ex) { throw new DuplicateClaim(ex); }
                        identities.recheck(identity, true);
                        Outcome<T> result = work.get();
                        if (result == null || result.status() < 200 || result.status() > 299) {
                            throw new IllegalArgumentException("Only committed success results are replayable");
                        }
                        request.setHttpStatus(result.status());
                        request.setResultSnapshot(snapshots.replayable(result.data()));
                        if (mapper.complete(request) != 1) throw new IllegalStateException("Lost idempotency claim");
                        return result;
                    });
                } catch (DuplicateClaim ex) {
                    // A duplicate insert aborts the whole transaction before the winning result is read.
                    return transactions.execute(tx -> {
                        identities.recheck(identity, true);
                        var committed = mapper.committed(request);
                        if (committed == null || committed.getHttpStatus() == null) {
                            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                        }
                        if (!request.getRequestHash().equals(committed.getRequestHash())) {
                            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                        }
                        return new Outcome<>(committed.getHttpStatus(), json.convertValue(committed.getResultSnapshot(), resultType));
                    });
                }
            } catch (RuntimeException ex) {
                if (!retryable(ex)) throw ex;
                if (attempt == 2) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                try { Thread.sleep(20L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private static boolean retryable(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)) return true;
        }
        return false;
    }

    private static final class DuplicateClaim extends RuntimeException {
        DuplicateClaim(DuplicateKeyException cause) { super(cause); }
    }
}
