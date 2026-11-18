package com.hiking.gymsaas.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class UtcTimes {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private UtcTimes() { }

    public static LocalDateTime toDatabase(OffsetDateTime time) {
        return LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC);
    }

    public static OffsetDateTime toApi(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC);
    }

    public static LocalDate businessDate(Instant instant) {
        return instant.atZone(BUSINESS_ZONE).toLocalDate();
    }
}
