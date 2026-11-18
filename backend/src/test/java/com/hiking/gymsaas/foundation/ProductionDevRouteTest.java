package com.hiking.gymsaas.foundation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles({"production", "local"})
@EnabledIfEnvironmentVariable(named = "FOUNDATION_DB_URL", matches = ".+")
class ProductionDevRouteTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { FoundationTestProperties.configure(registry); }
    @Autowired MockMvc mvc;

    @Test
    void evenAnAccidentalLocalProfileAndEnabledFlagCannotExposeDevelopmentRoutesInProduction() throws Exception {
        mvc.perform(post("/dev/auth/customer-login").servletPath("/dev/auth/customer-login")
                .header("X-Dev-Key", FoundationTestProperties.DEV_KEY).contentType("application/json")
                .content("{\"fixtureKey\":\"CUSTOMER_A\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/dev/auth/customer-login").servletPath("/dev/auth/customer-login")
                .header("Authorization", "Bearer invalid-token").contentType("application/json")
                .content("{\"fixtureKey\":\"CUSTOMER_A\"}"))
                .andExpect(status().isNotFound());
    }
}
