package com.hiking.gymsaas.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {
    public static final long MAX_SAFE_CENTS = 9_007_199_254_740_991L;

    private Money() { }

    // Signed amounts support adjustments; receipt/refund services must additionally require > 0.
    public static long toCents(BigDecimal yuan) {
        Objects.requireNonNull(yuan, "yuan");
        long cents = yuan.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        requireSafe(cents);
        return cents;
    }

    public static BigDecimal toYuan(long cents) {
        requireSafe(cents);
        return BigDecimal.valueOf(cents, 2);
    }

    public static void requireSafe(long cents) {
        if (cents < -MAX_SAFE_CENTS || cents > MAX_SAFE_CENTS) {
            throw new IllegalArgumentException("Amount exceeds JSON safe integer range");
        }
    }
}
