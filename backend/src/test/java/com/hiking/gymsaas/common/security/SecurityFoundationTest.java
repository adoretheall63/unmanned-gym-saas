package com.hiking.gymsaas.common.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hiking.gymsaas.account.service.IdentityService;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class SecurityFoundationTest {
    private static final byte[] KEY = "foundation-test-key-32-bytes-only!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final JwtTokens tokens = new JwtTokens(Base64.getEncoder().encodeToString(KEY));
    private final Identity admin = new Identity(Identity.Type.ADMIN, 7, 11L, 3, "ADMIN", List.of());

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void signedTokensCarryTypeVersionMerchantAndTwoHourLifetime() {
        var jwt = tokens.jwtDecoder().decode(tokens.issue(admin));
        assertEquals("7", jwt.getSubject());
        assertEquals("11", jwt.getClaimAsString("merchantId"));
        assertEquals("ADMIN", jwt.getClaimAsString("identityType"));
        assertEquals(7200, java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        assertFalse(jwt.getClaims().containsKey("permissions"));
    }

    @Test
    void invalidSignaturesExpiryIssuerAudienceAndMissingExpiryAreRejected() {
        var decoder = tokens.jwtDecoder();
        assertThrows(JwtException.class, () -> decoder.decode(signed("wrong", JwtTokens.ISSUER, Instant.now().plusSeconds(30), KEY)));
        assertThrows(JwtException.class, () -> decoder.decode(signed(JwtTokens.ISSUER, "other", Instant.now().plusSeconds(30), KEY)));
        assertThrows(JwtException.class, () -> decoder.decode(signed(JwtTokens.ISSUER, JwtTokens.ISSUER, Instant.now().minusSeconds(1), KEY)));
        assertThrows(JwtException.class, () -> decoder.decode(signed(JwtTokens.ISSUER, JwtTokens.ISSUER, null, KEY)));
        assertThrows(JwtException.class, () -> decoder.decode(signed(JwtTokens.ISSUER, JwtTokens.ISSUER,
                Instant.now().plusSeconds(30), "different-signing-key-32-bytes!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(IllegalStateException.class, () -> new JwtTokens(""));
    }

    @Test
    void permissionsComeFromCurrentDatabaseAndRevokedVersionsOrMerchantsFail() {
        var identities = mock(IdentityService.class);
        var converter = new DatabaseJwtAuthenticationConverter(identities);
        var jwt = tokens.jwtDecoder().decode(tokens.issue(admin));
        when(identities.load(Identity.Type.ADMIN, 7)).thenReturn(admin);
        var authentication = converter.convert(jwt);
        assertEquals(admin, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        when(identities.load(Identity.Type.ADMIN, 7)).thenReturn(new Identity(Identity.Type.ADMIN, 7, 11L, 4, "ADMIN", List.of()));
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
        when(identities.load(Identity.Type.ADMIN, 7)).thenReturn(new Identity(Identity.Type.ADMIN, 7, 12L, 3, "ADMIN", List.of()));
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }

    @Test
    void absentContextNeverCreatesADefaultMerchant() {
        var error = assertThrows(BusinessException.class, CurrentIdentity::merchantId);
        assertEquals(ErrorCode.UNAUTHENTICATED, error.code());
    }

    @Test
    void developmentAccessRequiresAnExplicitLocalProfileSwitchAndLongKey() {
        String key = "development-test-key-at-least-32-bytes";
        var local = new MockEnvironment().withProperty("spring.profiles.active", "local");
        local.setActiveProfiles("local");
        var access = new DevAccess(local, true, key);
        assertTrue(access.enabled());
        access.verify(key);
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(BusinessException.class, () -> access.verify("wrong")).code());
        assertFalse(new DevAccess(local, false, "").enabled());
        assertThrows(IllegalStateException.class, () -> new DevAccess(local, true, "short"));
        local.setActiveProfiles("local", "production");
        assertFalse(new DevAccess(local, true, key).enabled());
        local.setActiveProfiles("local", "real");
        assertFalse(new DevAccess(local, true, key).enabled());
    }

    private String signed(String issuer, String audience, Instant expires, byte[] key) {
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("7")
                .issuedAt(Instant.now().minusSeconds(5));
        if (expires != null) claims.expiresAt(expires);
        return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
