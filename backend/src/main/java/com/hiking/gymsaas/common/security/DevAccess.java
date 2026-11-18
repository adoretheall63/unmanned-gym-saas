package com.hiking.gymsaas.common.security;

import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class DevAccess {
    private final boolean enabled;
    private final String key;

    public DevAccess(Environment environment, @Value("${app.security.dev.enabled:false}") boolean enabled,
                     @Value("${app.security.dev.key:}") String key) {
        this.enabled = enabled && environment.acceptsProfiles(Profiles.of("(local | mock-demo) & !prod & !production & !real"));
        if (this.enabled && key.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("DEV_AUTH_KEY must contain at least 32 bytes when development login is enabled");
        }
        this.key = key;
    }

    public boolean enabled() { return enabled; }

    public void verify(String candidate) {
        if (!enabled) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (candidate == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
