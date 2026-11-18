package com.hiking.gymsaas.common.web;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestIds {
    public static final String MDC_KEY = "requestId";

    private RequestIds() { }

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
