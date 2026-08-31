package com.kologeski.interviewassistant.auth;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM identity_tokens"); jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM company_invitations"); jdbc.update("DELETE FROM company_memberships");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM companies WHERE id <> '00000000-0000-0000-0000-000000000001'");
    }

    @Test
    void verificationAndPasswordResetAreSingleUseRotatedAndAntiEnumerationSafe() throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Identity User\",\"email\":\"identity@example.test\",\"password\":\"OldPassword123!\",\"rememberMe\":true}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode registration = body(registered);
        String access = registration.get("accessToken").asText();
        String refresh = registration.get("refreshToken").asText();

        MvcResult verification = mvc.perform(post("/api/auth/email-verification/request")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isAccepted()).andReturn();
        String verificationToken = tokenFrom(verification);
        mvc.perform(post("/api/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verificationToken))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verificationToken))).andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.developmentPath").isEmpty());

        String first = tokenFrom(mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"identity@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("ACCEPTED")).andReturn());
        String second = tokenFrom(mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"identity@example.test\"}"))
                .andExpect(status().isAccepted()).andReturn());
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(first, "NewPassword123!"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(second, "NewPassword123!"))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(second, "OtherPassword123!"))).andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(refresh))).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me")
                        .header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("OldPassword123!"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("NewPassword123!"))).andExpect(status().isOk());
    }

    private String tokenFrom(MvcResult result) throws Exception {
        String path = body(result).get("developmentPath").asText();
        return path.substring(path.lastIndexOf('/') + 1);
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String tokenBody(String token) throws Exception { return json.writeValueAsString(java.util.Map.of("token", token)); }
    private String resetBody(String token, String password) throws Exception { return json.writeValueAsString(java.util.Map.of("token", token, "password", password)); }
    private String tokenRequest(String token) throws Exception { return json.writeValueAsString(java.util.Map.of("refreshToken", token)); }
    private String loginBody(String password) throws Exception { return json.writeValueAsString(java.util.Map.of("email", "identity@example.test", "password", password, "rememberMe", false)); }
}
