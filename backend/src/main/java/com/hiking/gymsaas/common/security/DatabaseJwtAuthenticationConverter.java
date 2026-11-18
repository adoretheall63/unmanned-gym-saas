package com.hiking.gymsaas.common.security;

import com.hiking.gymsaas.account.service.IdentityService;
import com.hiking.gymsaas.common.exception.BusinessException;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final IdentityService identities;

    public DatabaseJwtAuthenticationConverter(IdentityService identities) { this.identities = identities; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var type = Identity.Type.valueOf(jwt.getClaimAsString("identityType"));
            String subject = jwt.getSubject();
            if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
            long id = Long.parseLong(subject);
            Number version = jwt.getClaim("tokenVersion");
            if (id < 1 || version == null || version.longValue() < 0
                    || !version.toString().matches("[0-9]+")) throw new IllegalArgumentException();
            var identity = identities.load(type, id);
            String merchant = jwt.getClaimAsString("merchantId");
            if (identity.tokenVersion() != version.longValue()
                    || !Objects.equals(merchant, identity.merchantId() == null ? null : identity.merchantId().toString())) {
                throw new IllegalArgumentException();
            }
            return UsernamePasswordAuthenticationToken.authenticated(identity, null, identity.authorities());
        } catch (DataAccessException ex) {
            throw new AuthenticationServiceException("Identity store unavailable");
        } catch (BusinessException | IllegalArgumentException | ClassCastException | NullPointerException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
    }
}
