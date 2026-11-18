package com.hiking.gymsaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Application identities/JWT replace Boot's generated in-memory user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GymSaasApplication {
    public static void main(String[] args) {
        SpringApplication.run(GymSaasApplication.class, args);
    }
}
