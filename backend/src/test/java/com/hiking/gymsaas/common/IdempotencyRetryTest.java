package com.hiking.gymsaas.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.gymsaas.account.service.IdentityService;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.idempotency.IdempotencyService;
import com.hiking.gymsaas.common.idempotency.mapper.IdempotencyMapper;
import com.hiking.gymsaas.common.idempotency.model.entity.ApiRequest;
import com.hiking.gymsaas.common.security.Identity;
import com.hiking.gymsaas.common.util.JsonSnapshots;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class IdempotencyRetryTest {
    private final IdempotencyMapper mapper = mock(IdempotencyMapper.class);
    private final IdentityService identities = mock(IdentityService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ObjectMapper json = new ObjectMapper();
    private final IdempotencyService service = new IdempotencyService(mapper, identities, new JsonSnapshots(json), json, manager);

    @BeforeEach void identity() {
        var identity = new Identity(Identity.Type.ADMIN, 1, 2L, 0, "ADMIN", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(identity, null, identity.authorities()));
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(mapper.complete(any())).thenReturn(1);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(ints = {1213, 1205})
    void retriesTheEntireTransactionAfterDeadlockOrLockTimeout(int code) {
        var calls = new AtomicInteger();
        var result = execute(() -> {
            if (calls.incrementAndGet() == 1) throw sqlFailure(code);
            return new IdempotencyService.Outcome<>(200, Map.of("accepted", true));
        });
        assertEquals(true, result.data().get("accepted"));
        assertEquals(2, calls.get());
        verify(manager).rollback(any());
        verify(manager).commit(any());
    }

    @Test
    void unknownSqlErrorsAreNotRetried() {
        var failure = sqlFailure(1064);
        assertSame(failure, assertThrows(UncategorizedSQLException.class, () -> execute(() -> { throw failure; })));
        verify(mapper).claim(any());
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void exhaustedRetriesRollBackThreeTimesAndReportDependencyFailure() {
        var failure = assertThrows(BusinessException.class, () -> execute(() -> { throw sqlFailure(1213); }));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, failure.code());
        verify(mapper, times(3)).claim(any());
        verify(manager, times(3)).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void replayLockTimeoutAlsoRetriesWithoutRerunningBusinessWork() {
        when(mapper.claim(any())).thenThrow(new DuplicateKeyException("duplicate fixture"));
        var saved = new ApiRequest();
        saved.setHttpStatus(201);
        saved.setResultSnapshot(json.valueToTree(Map.of("id", "1")));
        when(mapper.committed(any())).thenThrow(sqlFailure(1205)).thenAnswer(invocation -> {
            saved.setRequestHash(((ApiRequest) invocation.getArgument(0)).getRequestHash());
            return saved;
        });
        var result = execute(() -> { throw new AssertionError("Replay must not execute business work"); });
        assertEquals(201, result.status());
        assertEquals("1", result.data().get("id"));
        verify(mapper, times(2)).claim(any());
        verify(manager, times(3)).rollback(any());
        verify(manager).commit(any());
    }

    private IdempotencyService.Outcome<Map> execute(Supplier<IdempotencyService.Outcome<Map>> work) {
        return service.execute("retryTest", "POST", "/api/v1/admin/test", "retry-key", Map.of(), Map.class, work);
    }

    private UncategorizedSQLException sqlFailure(int code) {
        return new UncategorizedSQLException("test", "fixture", new SQLException("injected SQL error", "HY000", code));
    }
}
