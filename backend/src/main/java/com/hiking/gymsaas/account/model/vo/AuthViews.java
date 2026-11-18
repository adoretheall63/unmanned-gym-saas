package com.hiking.gymsaas.account.model.vo;

import java.util.List;

public final class AuthViews {
    private AuthViews() { }

    public record Admin(String id, String merchantId, String username, String role, List<String> permissions) { }
    public record Customer(String id, String nickname, boolean phoneVerified) { }
    public record CustomerProfile(String id, String nickname, String phoneMasked, boolean phoneVerified) { }
    public record AdminToken(String accessToken, String tokenType, long expiresIn, Admin admin) {
        @Override public String toString() { return "AdminToken[credentials=REDACTED]"; }
    }
    public record CustomerToken(String accessToken, String tokenType, long expiresIn, Customer customer) {
        @Override public String toString() { return "CustomerToken[credentials=REDACTED]"; }
    }
    public record Accepted(boolean accepted) { }
    public record Merchant(String id, String name, String status) { }
}
