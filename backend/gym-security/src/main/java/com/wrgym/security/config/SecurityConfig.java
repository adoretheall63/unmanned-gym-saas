package com.wrgym.security.config;

import com.wrgym.security.auth.AccountStatusFilter;
import com.wrgym.security.jwt.JwtAuthenticationFilter;
import com.wrgym.security.jwt.JwtProperties;
import com.wrgym.security.jwt.JwtService;
import com.wrgym.security.web.RestAccessDeniedHandler;
import com.wrgym.security.web.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 双端鉴权（docs/接口文档.md §1.1 / §1.3）：顾客端 /api/v1/app/**，商家端 /api/v1/admin/**。
 *
 * <p>过滤器顺序：JWT 解析 → 每请求账号状态/token_version 复核（AccountStatusFilter）。
 * <p>所有失败响应统一 R（401/403），不返回 Spring 默认 HTML。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    /** 商家端：/api/v1/admin/** 仅 ROLE_ADMIN（登录接口放行）。 */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http,
                                                JwtAuthenticationFilter jwtFilter,
                                                AccountStatusFilter accountStatusFilter,
                                                RestAuthenticationEntryPoint entryPoint,
                                                RestAccessDeniedHandler deniedHandler) throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(accountStatusFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /** 顾客端 + 公共入口。 */
    @Bean
    @Order(2)
    public SecurityFilterChain appFilterChain(HttpSecurity http,
                                              JwtAuthenticationFilter jwtFilter,
                                              AccountStatusFilter accountStatusFilter,
                                              RestAuthenticationEntryPoint entryPoint,
                                              RestAccessDeniedHandler deniedHandler) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/app/auth/wechat-login").permitAll()
                        .requestMatchers("/api/v1/app/stores/**").permitAll()
                        .requestMatchers("/api/v1/app/auth/**").authenticated()
                        .requestMatchers("/api/v1/integrations/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(accountStatusFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
