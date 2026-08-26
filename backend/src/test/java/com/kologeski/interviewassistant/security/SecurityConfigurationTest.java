package com.kologeski.interviewassistant.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedUserCannotAccessApplication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserAccessDoesNotDependOnRole() throws Exception {
        mvc.perform(get("/actuator/health").with(user("ordinary-user-a").authorities()))
                .andExpect(status().isOk());
    }

    @Test
    void anotherAuthenticatedUserHasTheSameAccessWithoutAuthorities() throws Exception {
        mvc.perform(get("/actuator/health").with(user("ordinary-user-b").authorities()))
                .andExpect(status().isOk());
    }
}
