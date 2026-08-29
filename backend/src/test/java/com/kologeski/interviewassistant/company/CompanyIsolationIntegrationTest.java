package com.kologeski.interviewassistant.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyIsolationIntegrationTest {
    private static final UUID LEGACY_COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM interview_requirement_snapshots");
        jdbc.update("DELETE FROM interviews");
        jdbc.update("DELETE FROM job_vacancy_requirements");
        jdbc.update("DELETE FROM job_vacancies");
        jdbc.update("DELETE FROM company_invitations");
        jdbc.update("DELETE FROM company_memberships");
        jdbc.update("DELETE FROM interview_availability");
        jdbc.update("DELETE FROM user_technical_skills");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM companies WHERE id <> ?", LEGACY_COMPANY_ID);
    }

    @Test
    void vacanciesAndInterviewersAreIsolatedAndInviteLinkAddsTheExpectedUser() throws Exception {
        String adminA = register("admin-a@empresa-a.com");
        String adminB = register("admin-b@empresa-b.com");
        UUID companyA = createCompany(adminA, "Empresa A");
        UUID companyB = createCompany(adminB, "Empresa B");

        createVacancy(adminA, companyA, "Backend Empresa A");
        createVacancy(adminB, companyB, "Frontend Empresa B");

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Backend Empresa A"));

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyB).param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());

        MvcResult invitation = mvc.perform(post("/api/companies/invitations")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"entrevistador@empresa-a.com\",\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").isNotEmpty())
                .andReturn();
        String path = body(invitation).get("path").asText();
        String invitationToken = path.substring(path.lastIndexOf('/') + 1);

        mvc.perform(get("/api/public/invitations/{token}", invitationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("entrevistador@empresa-a.com"));

        String interviewer = register("entrevistador@empresa-a.com");
        mvc.perform(post("/api/companies/invitations/accept")
                        .header("Authorization", bearer(interviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", invitationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyA.toString()));

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Backend Empresa A"));

        mvc.perform(get("/api/companies/members").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email == 'entrevistador@empresa-a.com')].role").value("INTERVIEWER"));

        mvc.perform(post("/api/companies/invitations/accept")
                        .header("Authorization", bearer(interviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", invitationToken))))
                .andExpect(status().isConflict());
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário Teste\",\"email\":\"%s\",\"password\":\"123456\",\"rememberMe\":false}"
                                .formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return body(result).get("accessToken").asText();
    }

    private UUID createCompany(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/companies").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }

    private void createVacancy(String token, UUID companyId, String title) throws Exception {
        mvc.perform(post("/api/job-positions").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}"
                                .formatted(title)))
                .andExpect(status().isOk());
    }

    private String bearer(String token) { return "Bearer " + token; }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
