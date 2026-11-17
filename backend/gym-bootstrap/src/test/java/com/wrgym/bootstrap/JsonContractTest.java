package com.wrgym.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.api.PageQuery;
import com.wrgym.common.api.R;
import com.wrgym.common.config.JacksonConfig;
import com.wrgym.common.web.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约基础单元测试（纯 JUnit，不启动 Spring 上下文、不依赖数据库/Docker）：
 * 统一响应结构、错误码到 HTTP 状态、分页默认值、时间与数值的 JSON 口径。
 */
class JsonContractTest {

    private static ObjectMapper objectMapper;

    @BeforeAll
    static void buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().gymJacksonCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void successBodyHasContractFields() throws Exception {
        RequestContext.setRequestId("req-test-1");
        String json = objectMapper.writeValueAsString(R.ok(Map.of("expiresIn", 7200L, "id", "101")));
        assertThat(json).contains("\"code\":\"OK\"")
                .contains("\"message\":\"ok\"")
                .contains("\"requestId\":\"req-test-1\"")
                .contains("\"serverTime\"")
                .contains("\"data\"");
        // 契约：expiresIn 等计数值是 JSON 整数，不能转字符串
        assertThat(json).contains("\"expiresIn\":7200");
        // 时间必须带时区（Z）
        assertThat(json).containsPattern("\"serverTime\":\"[0-9T:.-]+Z\"");
    }

    @Test
    void failureBodyHasFieldErrorsArray() throws Exception {
        String json = objectMapper.writeValueAsString(R.fail(ErrorCode.SLOT_FULL));
        assertThat(json).contains("\"code\":\"SLOT_FULL\"").contains("\"fieldErrors\":[]");
        assertThat(json).doesNotContain("\"data\"");
    }

    @Test
    void nullableContractFieldsAreSerializedAsNull() throws Exception {
        // 契约要求"可为 null 的必有字段"显式输出 null（例如 nickname / phoneMasked）
        record Profile(String id, String nickname, String phoneMasked) {
        }
        String json = objectMapper.writeValueAsString(new Profile("101", null, null));
        assertThat(json).isEqualTo("{\"id\":\"101\",\"nickname\":null,\"phoneMasked\":null}");
    }

    @Test
    void errorCodeMapsToDocumentedHttpStatus() {
        assertThat(ErrorCode.SLOT_FULL.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.IDEMPOTENCY_CONFLICT.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.UNAUTHENTICATED.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.PRECONDITION_REQUIRED.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(ErrorCode.ETAG_MISMATCH.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(ErrorCode.CARD_FROZEN.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.INTEGRATION_NOT_VERIFIED.httpStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(ErrorCode.PHONE_BINDING_UNAVAILABLE.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void pageQueryUsesDocumentedDefaultsAndClamps() {
        PageQuery query = new PageQuery();
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
        PageQuery tooLarge = new PageQuery();
        tooLarge.setPageSize(500);
        assertThat(tooLarge.getPageSize()).isEqualTo(100);
    }

    @Test
    void localDateTimeSerializedAsUtcIsoWithZone() throws Exception {
        String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 11, 16, 14, 0, 0));
        assertThat(json).isEqualTo("\"2026-11-16T14:00:00Z\"");
    }

    @Test
    void idempotencyHashIsStableAndSensitiveToBody() {
        String a = com.wrgym.common.idempotency.IdempotencyService
                .hash("op", "CUSTOMER", 1L, "{\"nickname\":\"a\"}");
        String b = com.wrgym.common.idempotency.IdempotencyService
                .hash("op", "CUSTOMER", 1L, "{\"nickname\":\"a\"}");
        String c = com.wrgym.common.idempotency.IdempotencyService
                .hash("op", "CUSTOMER", 1L, "{\"nickname\":\"b\"}");
        assertThat(a).hasSize(64).isEqualTo(b).isNotEqualTo(c);
    }
}
