package com.hiking.gymsaas.common;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.util.JsonSnapshots;
import com.hiking.gymsaas.common.web.ConfigurationEtags;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoundationUtilityTest {
    private final JsonSnapshots snapshots = new JsonSnapshots(new ObjectMapper());

    @Test
    void canonicalJsonSortsNestedObjectsButPreservesArrayOrder() throws Exception {
        var mapper = new ObjectMapper();
        assertEquals(snapshots.canonical(mapper.readTree("{\"b\":2,\"a\":{\"d\":4,\"c\":3}}")),
                snapshots.canonical(mapper.readTree("{\"a\":{\"c\":3,\"d\":4},\"b\":2}")));
        assertNotEquals(snapshots.canonical(List.of(1, 2)), snapshots.canonical(List.of(2, 1)));
    }

    @Test
    void auditRedactsNestedSecretsAndReplayRejectsThem() {
        var value = Map.of("before", Map.of("password", "sensitive", "name", "visible"),
                "items", List.of(Map.of("access_token", "sensitive")));
        var redacted = snapshots.redact(value);
        assertEquals("[REDACTED]", redacted.path("before").path("password").asText());
        assertEquals("visible", redacted.path("before").path("name").asText());
        assertFalse(redacted.toString().contains("sensitive"));
        assertThrows(IllegalArgumentException.class, () -> snapshots.replayable(value));
        assertThrows(IllegalArgumentException.class, () -> snapshots.replayable(Map.of("data", "x".repeat(65537))));
    }

    @Test
    void etagsRequireAnExactConfigurationVersion() {
        String current = ConfigurationEtags.of("merchant-name");
        ConfigurationEtags.requireMatch(current, current);
        assertEquals(ErrorCode.PRECONDITION_REQUIRED,
                assertThrows(BusinessException.class, () -> ConfigurationEtags.requireMatch(null, current)).code());
        assertEquals(ErrorCode.ETAG_MISMATCH,
                assertThrows(BusinessException.class, () -> ConfigurationEtags.requireMatch("*", current)).code());
    }
}
