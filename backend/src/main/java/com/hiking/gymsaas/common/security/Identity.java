package com.hiking.gymsaas.common.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record Identity(Type type, long id, Long merchantId, long tokenVersion,
                       String role, List<String> permissions) {
    public enum Type { CUSTOMER, ADMIN }

    public Identity {
        permissions = List.copyOf(permissions);
    }

    public List<SimpleGrantedAuthority> authorities() {
        if (type == Type.CUSTOMER) return List.of(new SimpleGrantedAuthority("CUSTOMER"));
        var result = new java.util.ArrayList<SimpleGrantedAuthority>();
        result.add(new SimpleGrantedAuthority("ADMIN_IDENTITY"));
        if ("ADMIN".equals(role)) result.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        permissions.forEach(p -> result.add(new SimpleGrantedAuthority(p)));
        return List.copyOf(result);
    }
}
