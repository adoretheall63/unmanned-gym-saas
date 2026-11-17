package com.wrgym.security.jwt;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验（docs/接口文档.md §1.1、§1.3）。
 *
 * <p>claims：{@code sub}=业务主体ID、{@code type}=CUSTOMER/ADMIN、{@code name}、{@code ver}=token_version、
 * {@code mid}=后台账号所属商家（顾客无此 claim）、{@code perms}=后台权限码。
 * <p>token_version 的失效判断需要账号状态，由 auth 模块在认证时读取数据库复核（本类只校验签名与有效期）。
 */
@Service
public class JwtService {

    public enum PrincipalType { CUSTOMER, ADMIN }

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.getSecret() == null || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("gym.jwt.secret 未配置或长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String signCustomer(long userId, int tokenVersion, String nickname) {
        return sign(PrincipalType.CUSTOMER, userId, tokenVersion, nickname, null, null);
    }

    public String signAdmin(long adminId, long merchantId, int tokenVersion, String username, java.util.List<String> permissions) {
        return sign(PrincipalType.ADMIN, adminId, tokenVersion, username, merchantId, permissions);
    }

    private String sign(PrincipalType type, long subjectId, int tokenVersion, String name,
                        Long merchantId, java.util.List<String> permissions) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(properties.getExpireSeconds()));
        var builder = Jwts.builder()
                .subject(Long.toString(subjectId))
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claim("type", type.name())
                .claim("ver", tokenVersion)
                .claim("name", name);
        if (merchantId != null) {
            builder.claim("mid", Long.toString(merchantId));
        }
        if (permissions != null && !permissions.isEmpty()) {
            builder.claim("perms", permissions);
        }
        return builder.signWith(key).compact();
    }

    /** 解析并校验签名/有效期；失败统一 401。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).requireIssuer(properties.getIssuer())
                    .build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
