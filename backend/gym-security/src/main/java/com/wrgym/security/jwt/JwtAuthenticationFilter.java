package com.wrgym.security.jwt;

import com.wrgym.common.exception.BizException;
import com.wrgym.common.security.PrincipalRef;
import com.wrgym.common.security.SecurityPrincipal;
import com.wrgym.security.auth.LoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 {@code Authorization: Bearer <jwt>} 并填充 SecurityContext / PrincipalRef。
 *
 * <p>只负责令牌有效性与身份类型；账号停用、token_version 失效、商家状态由
 * {@link com.wrgym.security.auth.AccountStatusFilter} 每请求复核。
 * 令牌无效时不抛异常，交给 EntryPoint 统一返回 401。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtService.parse(token);
                long id = Long.parseLong(claims.getSubject());
                LoginUser.Type type = LoginUser.Type.valueOf(String.valueOf(claims.get("type")));
                String name = claims.get("name", String.class);
                int version = claims.get("ver") == null ? 0 : ((Number) claims.get("ver")).intValue();
                Long merchantId = claims.get("mid") == null ? null : Long.parseLong(String.valueOf(claims.get("mid")));
                List<String> permissions = new ArrayList<>();
                Object perms = claims.get("perms");
                if (perms instanceof List<?> list) {
                    list.forEach(item -> permissions.add(String.valueOf(item)));
                }
                LoginUser loginUser = new LoginUser(id, type, name, merchantId, version, List.copyOf(permissions));
                var granted = new ArrayList<SimpleGrantedAuthority>();
                granted.add(new SimpleGrantedAuthority(type == LoginUser.Type.ADMIN ? "ROLE_ADMIN" : "ROLE_CUSTOMER"));
                permissions.forEach(p -> granted.add(new SimpleGrantedAuthority("PERM_" + p)));
                var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, granted);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                PrincipalRef.set(new SecurityPrincipal(id,
                        type == LoginUser.Type.ADMIN ? SecurityPrincipal.ADMIN : SecurityPrincipal.CUSTOMER));
            } catch (BizException ex) {
                log.debug("invalid jwt: {}", ex.getMessage());
            } catch (RuntimeException ex) {
                log.debug("unparsable jwt claims", ex);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            PrincipalRef.clear();
        }
    }
}
