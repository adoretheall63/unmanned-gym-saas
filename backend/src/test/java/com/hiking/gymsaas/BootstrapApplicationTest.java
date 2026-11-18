package com.hiking.gymsaas;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bootstrap")
class BootstrapApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;

    @Test
    void healthStartsWithoutPretendingInfrastructureIsConnected() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void bootstrapBusinessEndpointsRemainClosed() throws Exception {
        mvc.perform(post("/app/orders").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/admin/orders").with(user("demo").roles("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void passwordsUseBcryptAndDoNotStorePlaintext() {
        String raw = "a-test-password";
        String hash = passwords.encode(raw);
        assertNotEquals(raw, hash);
        assertTrue(passwords.matches(raw, hash));
        assertFalse(passwords.matches("incorrect", hash));
    }
}
