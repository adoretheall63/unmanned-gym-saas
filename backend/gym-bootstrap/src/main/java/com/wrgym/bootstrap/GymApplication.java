package com.wrgym.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 无人健身房 SaaS 后端启动类（模块化单体，单进程部署）。
 *
 * <p>装配约定：各模块包根为 {@code com.wrgym.*}，Mapper 统一放在 {@code com.wrgym.<模块>.mapper}
 * （含 {@code com.wrgym.common.idempotency.mapper}）。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.wrgym")
@ConfigurationPropertiesScan(basePackages = "com.wrgym")
@MapperScan("com.wrgym.**.mapper")
@EnableScheduling
public class GymApplication {

    public static void main(String[] args) {
        SpringApplication.run(GymApplication.class, args);
    }
}
