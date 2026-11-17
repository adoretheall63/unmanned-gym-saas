package com.wrgym.common.web;

/**
 * 当前请求上下文（requestId）。由 {@link RequestIdFilter} 写入，响应体 R 读取。
 * 使用后必须在 finally 中 {@link #clear()}，避免线程池复用串号。
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String currentRequestId() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
