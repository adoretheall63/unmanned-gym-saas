package com.wrgym.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 分组（docs/接口文档.md §1.1：顾客端 /api/v1/app/**、商家端 /api/v1/admin/**）。
 *
 * <p>机器可读契约以 api/first-loop.openapi.yaml 与接口文档为准；
 * Swagger 只用于本地联调，不作为契约来源。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gymOpenApi() {
        return new OpenAPI().info(new Info()
                .title("无人健身房 SaaS API")
                .version("v1")
                .description("契约见 docs/接口文档.md v1.2；实现状态以代码与测试为准"));
    }

    @Bean
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("customer")
                .pathsToMatch("/api/v1/app/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
}
