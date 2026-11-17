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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约基础单元测试（纯 JUnit，不启动 Spring 上下文、不依赖数据库/Docker）：
 * 统一响应结构、错误码到 HTTP 状态、分页默认值、Long 与时间的 JSON 口径。
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
        String json = objectMapper.writeValueAsString(R.ok(Map.of("orderId", 123456789012345678L)));
        assertThat(json).contains("\"code\":\"OK\"")
                .contains("\"message\":\"ok\"")
                .contains("\"requestId\":\"req-test-1\"")
                .contains("\"serverTime\"")
                .contains("\"data\"");
        // Long 必须序列化为字符串，避免 JS 精度丢失
        assertThat(json).contains("\"orderId\":\"123456789012345678\"");
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
    void errorCodeMapsToDocumentedHttpStatus() {
        assertThat(ErrorCode.SLOT_FULL.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.IDEMPOTENCY_CONFLICT.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.UNAUTHENTICATED.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.PRECONDITION_REQUIRED.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(ErrorCode.ETAG_MISMATCH.httpStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(ErrorCode.CARD_FROZEN.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.INTEGRATION_NOT_VERIFIED.httpStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
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
}
