package com.hiking.gymsaas.common.web;

import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.hiking.gymsaas.common.util.Hashes;

public final class ConfigurationEtags {
    private ConfigurationEtags() { }
    public static String of(String canonicalConfiguration) { return "\"" + Hashes.sha256(canonicalConfiguration) + "\""; }
    public static void requireMatch(String supplied, String current) {
        if (supplied == null || supplied.isBlank()) throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED);
        if (!supplied.equals(current)) throw new BusinessException(ErrorCode.ETAG_MISMATCH);
    }
}
