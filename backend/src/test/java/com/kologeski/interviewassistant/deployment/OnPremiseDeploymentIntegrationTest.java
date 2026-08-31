package com.kologeski.interviewassistant.deployment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.deployment.mode=ON_PREMISE")
@AutoConfigureMockMvc
class OnPremiseDeploymentIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesSafeCapabilitiesAndBlocksPublicRegistration() throws Exception {
        mvc.perform(get("/api/public/deployment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ON_PREMISE"))
                .andExpect(jsonPath("$.publicSignupEnabled").value(false))
                .andExpect(jsonPath("$.multiTenantEnabled").value(false))
                .andExpect(jsonPath("$.billingEnabled").value(false));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Admin","email":"admin@example.com","password":"strong-password","rememberMe":false}
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Cadastro público indisponível nesta instalação."));
    }
}
