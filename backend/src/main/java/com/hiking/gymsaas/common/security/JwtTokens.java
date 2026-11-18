package com.hiking.gymsaas.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@Profile("!bootstrap")
public class JwtTokens {
    public static final long EXPIRES_IN = 7200;
    public static final String ISSUER = "gym-saas";
    private final NimbusJwtEncoder encoder;
    private final SecretKeySpec key;

    public JwtTokens(@Value("${app.security.jwt-secret:}") String secret) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET must be base64 encoded"); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>("aud", a -> a != null && a.contains(ISSUER)),
                new JwtClaimValidator<Instant>("exp", java.util.Objects::nonNull)));
        return decoder;
    }

    public String issue(Identity identity) {
        var now = Instant.now();
        var builder = JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(ISSUER))
                .subject(Long.toString(identity.id())).issuedAt(now).expiresAt(now.plusSeconds(EXPIRES_IN))
                .id(UUID.randomUUID().toString()).claim("identityType", identity.type().name())
                .claim("tokenVersion", identity.tokenVersion());
        if (identity.merchantId() != null) builder.claim("merchantId", Long.toString(identity.merchantId()));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                builder.build())).getTokenValue();
    }
}
