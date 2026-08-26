package com.kologeski.interviewassistant.auth;

import com.fasterxml.jackson.databind.*;
import com.kologeski.interviewassistant.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() { refreshTokens.deleteAll(); users.deleteAll(); }

    @Test void registerLoginAndCurrentUserAreInternal() throws Exception {
        MvcResult registration = register("user@example.com", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        String access = body(registration).get("accessToken").asText();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"wrong\",\"rememberMe\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void duplicateNormalizedEmailReturnsConflict() throws Exception {
        register("duplicate@example.com", false).andExpect(status().isCreated());
        register("DUPLICATE@example.com", false).andExpect(status().isConflict());
    }

    @Test void rememberMeRefreshRotatesAndLogoutRevokesSession() throws Exception {
        MvcResult registered = register("persistent@example.com", true)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.refreshToken", notNullValue())).andReturn();
        String first = body(registered).get("refreshToken").asText();
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(token(first))).andExpect(status().isOk()).andReturn();
        String second = body(refreshed).get("refreshToken").asText();
        assertNotEquals(first, second);
        assertEquals(2, refreshTokens.count());
        assertEquals(1, refreshTokens.findAll().stream().filter(RefreshToken::isRevoked).count());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(token(first)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(token(second)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(token(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test void expiredRefreshTokenIsRejected() throws Exception {
        String raw = body(register("expired@example.com", true).andReturn()).get("refreshToken").asText();
        RefreshToken token = refreshTokens.findAll().getFirst();
        token.expireAt(Instant.now().minusSeconds(1)); refreshTokens.save(token);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(token(raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test void authenticatedUserOwnsVacancyAuditAndInterviewIdentity() throws Exception {
        MvcResult registration = register("auditor@example.com", false).andReturn();
        String access = body(registration).get("accessToken").asText();
        String userId = String.valueOf(users.findByEmail("auditor@example.com").orElseThrow().getId());

        MvcResult vacancy = mvc.perform(post("/api/job-positions")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Backend\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value(userId)).andReturn();
        String vacancyId = body(vacancy).get("id").asText();

        mvc.perform(post("/api/interviews").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":\"%s\",\"candidateName\":\"Candidato\"}".formatted(vacancyId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.interviewerId").value(userId))
                .andExpect(jsonPath("$.createdBy").value(userId));
    }

    private ResultActions register(String email, boolean remember) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Usuário Teste\",\"email\":\"%s\",\"password\":\"123456\",\"rememberMe\":%s}"
                        .formatted(email, remember)));
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String token(String value) { return "{\"refreshToken\":\"%s\"}".formatted(value); }
}
