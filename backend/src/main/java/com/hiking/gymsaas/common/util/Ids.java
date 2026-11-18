package com.hiking.gymsaas.common.util;

public final class Ids {
    private Ids() { }

    public static long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("ID must be a positive decimal Long string");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ID exceeds Long.MAX_VALUE", ex);
        }
    }

    public static String format(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }
        return Long.toString(value);
    }
}
