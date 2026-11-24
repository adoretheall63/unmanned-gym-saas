package com.wrgym.common.web;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 配置类资源的 ETag 与 If-Match 约定（docs/接口文档.md §1.2）。
 *
 * <p>ETag 由**可写配置字段**即时计算（不新增 version 列），格式为强校验 {@code "hex"}；
 * 写接口必须带 {@code If-Match}：缺失 → 428 PRECONDITION_REQUIRED，不匹配 → 412 ETAG_MISMATCH。
 * <p>加锁后必须**用锁内读取的字段重新计算并再次比对**，不能只校验请求到达时的值。
 */
public final class Etags {

    private Etags() {
    }

    /**
     * 由可写配置字段生成 ETag。
     *
     * @param parts 参与摘要的字段，按固定顺序传入；null 视为空串
     */
    public static String of(Object... parts) {
        String joined = Stream.of(parts)
                .map(part -> part == null ? "" : String.valueOf(part))
                .collect(Collectors.joining("|"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash).substring(0, 32) + "\"";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 校验 If-Match；本方法应在获得锁、用锁内字段重新计算 currentEtag 之后调用。 */
    public static void verify(String ifMatchHeader, String currentEtag) {
        String provided = normalize(ifMatchHeader);
        if (provided == null) {
            throw new BizException(ErrorCode.PRECONDITION_REQUIRED);
        }
        if (!provided.equals(normalize(currentEtag))) {
            throw new BizException(ErrorCode.ETAG_MISMATCH);
        }
    }

    /** 归一化：去掉 W/ 前缀、首尾引号与空白；空值返回 null。 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
