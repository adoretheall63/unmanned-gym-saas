package com.wrgym.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 时间口径（docs/接口文档.md §1.1、docs/数据库设计.md 通用约定）。
 *
 * <ul>
 *   <li>存储与比较：UTC（{@code DATETIME(3)}，连接 time_zone=+00:00）；</li>
 *   <li>营业日、营业时间、统计日：Asia/Shanghai；</li>
 *   <li>API 输出：带时区 ISO 8601（见 JacksonConfig）。</li>
 * </ul>
 */
public final class TimeZones {

    /** 业务时区：营业日、营业时间与统计口径。 */
    public static final ZoneId BUSINESS = ZoneId.of("Asia/Shanghai");

    private TimeZones() {
    }

    /** 业务时区的"现在"对应的 UTC LocalDateTime（用于与数据库列直接比较）。 */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 业务时区的本地日期（今天）。 */
    public static LocalDate today() {
        return LocalDate.now(BUSINESS);
    }

    /** 业务时区的本地日期时间 → UTC 存储值。 */
    public static LocalDateTime businessToUtc(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atZone(BUSINESS).withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public static LocalDateTime businessToUtc(LocalDateTime businessDateTime) {
        return businessDateTime.atZone(BUSINESS).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** UTC 存储值 → 业务时区本地日期时间（展示与日报口径）。 */
    public static LocalDateTime utcToBusiness(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(BUSINESS).toLocalDateTime();
    }

    /** 业务日 [00:00, 次日 00:00) 对应的 UTC 区间，用于按日统计。 */
    public static LocalDateTime businessDayStartUtc(LocalDate date) {
        return date.atStartOfDay(BUSINESS).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime businessDayEndUtc(LocalDate date) {
        return date.plusDays(1).atStartOfDay(BUSINESS).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static Instant toInstant(LocalDateTime utcDateTime) {
        return utcDateTime.toInstant(ZoneOffset.UTC);
    }
}
