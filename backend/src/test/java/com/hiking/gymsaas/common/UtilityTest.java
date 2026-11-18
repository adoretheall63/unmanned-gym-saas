package com.hiking.gymsaas.common;

import static org.junit.jupiter.api.Assertions.*;

import com.hiking.gymsaas.common.util.Ids;
import com.hiking.gymsaas.common.util.Money;
import com.hiking.gymsaas.common.util.UtcTimes;
import jakarta.validation.Validation;
import com.hiking.gymsaas.common.page.PageQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilityTest {
    @Test
    void idsPreserveLongPrecision() {
        assertEquals(Long.MAX_VALUE, Ids.parse("9223372036854775807"));
        assertEquals("9223372036854775807", Ids.format(Long.MAX_VALUE));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "01", "-1", "+1", " 1", "1 ", "１", "1.0", "9223372036854775808"})
    void invalidIdentifiersAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> Ids.parse(value));
    }

    @Test
    void monetaryConversionIsExactAndJsonSafe() {
        assertEquals(1001, Money.toCents(new BigDecimal("10.01")));
        assertEquals(-100, Money.toCents(new BigDecimal("-1")));
        assertEquals(new BigDecimal("10.01"), Money.toYuan(1001));
        assertEquals(Money.MAX_SAFE_CENTS, Money.toCents(new BigDecimal("90071992547409.91")));
        assertThrows(ArithmeticException.class, () -> Money.toCents(new BigDecimal("0.001")));
        assertThrows(IllegalArgumentException.class, () -> Money.toYuan(Money.MAX_SAFE_CENTS + 1));
        assertThrows(IllegalArgumentException.class, () -> Money.toYuan(Long.MIN_VALUE));
    }

    @Test
    void businessDateAndDatabaseTimeUseDifferentZones() {
        var api = OffsetDateTime.parse("2026-11-16T00:30:00+08:00");
        var stored = UtcTimes.toDatabase(api);
        assertEquals("2026-11-15T16:30", stored.toString());
        assertEquals(api.toInstant(), UtcTimes.toApi(stored).toInstant());
        assertEquals("2026-11-16", UtcTimes.businessDate(Instant.parse("2026-11-15T16:30:00Z")).toString());
    }

    @Test
    void paginationRejectsUnboundedOrNullParameters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var query = new PageQuery();
            assertTrue(validator.validate(query).isEmpty());
            query.setPageSize(101);
            assertFalse(validator.validate(query).isEmpty());
            query.setPageSize(null);
            assertFalse(validator.validate(query).isEmpty());
        }
    }
}
