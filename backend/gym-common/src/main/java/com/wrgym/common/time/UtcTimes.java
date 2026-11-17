package com.wrgym.common.time;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 数据库与业务统一使用 UTC（docs/数据库设计.md 通用约定：时间点 UTC DATETIME(3)，连接 time_zone=+00:00）。
 * 展示层时区换算（Asia/Shanghai）在接口/报表层完成，不改变存储口径。
 */
public final class UtcTimes {

    private UtcTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
