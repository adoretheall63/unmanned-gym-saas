package com.hiking.gymsaas.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.hiking.gymsaas.common.security.MerchantIsolationInterceptor;

@Configuration
public class MybatisPlusConfiguration {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new MerchantIsolationInterceptor());
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
