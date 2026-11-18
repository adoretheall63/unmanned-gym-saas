package com.hiking.gymsaas.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hiking.gymsaas.common.web.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class WebContractTest {
    MockMvc mvc;

    @BeforeEach
    void setup() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void validationErrorsMatchEnvelopeAndTracingIsCleared() throws Exception {
        var result = mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.serverTime").isString()).andReturn();
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals(result.getResponse().getHeader("X-Request-Id"), tree.get("requestId").asText());
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @Test
    void unknownFieldsAreRejectedRatherThanSilentlyBound() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ok\",\"merchantId\":\"999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unexpectedErrorDoesNotEchoSecrets() throws Exception {
        mvc.perform(get("/probe/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-example"))));
        assertNull(MDC.get(RequestIds.MDC_KEY));
    }

    @RestController
    static class ProbeController {
        record Input(@NotBlank String name) { }
        @PostMapping("/probe")
        ApiResponse<Map<String, String>> create(@Valid @RequestBody Input input) {
            return ApiResponse.ok(Map.of("name", input.name()));
        }
        @GetMapping("/probe/error")
        void error() { throw new IllegalStateException("secret-example"); }
    }
}
