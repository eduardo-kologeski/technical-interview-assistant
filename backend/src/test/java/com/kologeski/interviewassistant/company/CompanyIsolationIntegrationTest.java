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

import static org.hamcrest.Matchers.containsInAnyOrder;
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
        com.kologeski.interviewassistant.PostgresqlTestDatabase.reset(jdbc);
    }

    @Test
    void auditTrailIsTenantScopedAndRestrictedToAdministrators() throws Exception {
        String adminA = register("audit-admin-a@empresa.com");
        String adminB = register("audit-admin-b@empresa.com");
        UUID companyA = createCompany(adminA, "Audit A");
        UUID companyB = createCompany(adminB, "Audit B");

        mvc.perform(get("/api/audit-events").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("COMPANY_CREATED"));
        mvc.perform(get("/api/audit-events").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isForbidden());
    }

    @Test
    void vacanciesAndInterviewersAreIsolatedAndInviteLinkAddsTheExpectedUser() throws Exception {
        String adminA = register("admin-a@empresa-a.com");
        String adminB = register("admin-b@empresa-b.com");
        UUID companyA = createCompany(adminA, "Empresa A");
        UUID companyB = createCompany(adminB, "Empresa B");

        String vacancyA = createVacancy(adminA, companyA, "Backend Empresa A");
        String vacancyB = createVacancy(adminB, companyB, "Frontend Empresa B");

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Backend Empresa A"));

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyB).param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/job-positions/{id}", vacancyA).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/job-positions/{id}", vacancyA).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tenant escape\",\"seniority\":\"Sênior\",\"requirements\":[],\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/job-positions/{id}/deactivate", vacancyA)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("query", "Frontend Empresa B"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(post("/api/interviews").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":\"%s\",\"candidateName\":\"Tenant escape\"}".formatted(vacancyB)))
                .andExpect(status().isNotFound());

        MvcResult invitation = mvc.perform(post("/api/companies/invitations")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"entrevistador@empresa-a.com\",\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.developmentPath").isNotEmpty())
                .andReturn();
        String path = body(invitation).get("developmentPath").asText();
        String invitationToken = path.substring(path.lastIndexOf('/') + 1);

        mvc.perform(get("/api/public/invitations/{token}", invitationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("entrevistador@empresa-a.com"));

        String interviewer = register("entrevistador@empresa-a.com");
        mvc.perform(post("/api/companies/invitations/accept")
                        .header("Authorization", bearer(interviewer)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", invitationToken))))
                .andExpect(status().isForbidden());
        verifyEmail(interviewer);
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

        mvc.perform(get("/api/audit-events").header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/job-positions").header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alteração indevida\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/companies/members").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email == 'entrevistador@empresa-a.com')].role").value("INTERVIEWER"));

        mvc.perform(get("/api/companies/members/page").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(patch("/api/companies/current").header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alteração indevida\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/companies/current").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Empresa A Renovada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Empresa A Renovada"));

        mvc.perform(get("/api/interviewers").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/interviewers").header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        Long interviewerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "entrevistador@empresa-a.com");
        Long adminAId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "admin-a@empresa-a.com");
        mvc.perform(patch("/api/companies/members/{userId}/role", adminAId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/companies/members/{userId}/role", interviewerId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"COMPANY_ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("COMPANY_ADMIN"));
        mvc.perform(patch("/api/companies/members/{userId}/role", interviewerId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("INTERVIEWER"));
        MvcResult createdInterview = mvc.perform(post("/api/interviews").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":\"%s\",\"candidateName\":\"Candidato compartilhado\",\"participantUserIds\":[%d]}".formatted(vacancyA, interviewerId)))
                .andExpect(status().isOk()).andReturn();
        String interviewId = body(createdInterview).get("id").asText();
        String candidateId = body(createdInterview).get("candidateId").asText();

        mvc.perform(get("/api/candidates").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("q", "compartilhado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Candidato compartilhado"));
        mvc.perform(get("/api/candidates/{id}", candidateId).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/candidates").header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));

        mvc.perform(get("/api/interviews").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/interviews").header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/interviews/{id}", interviewId).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/start", interviewId).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/complete", interviewId).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/assessment/finalize", interviewId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/assessment", interviewId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"assessmentData\":\"{}\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/interviews/{id}/pdf-generation", interviewId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/workspace", interviewId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"assessmentData\":\"{}\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}/workspace/complete", interviewId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/interviews/{id}/workspace", interviewId).header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"assessmentData\":\"{\\\"finalAssessment\\\":\\\"Parecer do gestor\\\"}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessmentData").value("{\"finalAssessment\":\"Parecer do gestor\"}"));

        mvc.perform(get("/api/interviews/{id}/workspace", interviewId).header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.assessmentData").value("{}"))
                .andExpect(jsonPath("$.participants.length()").value(2));

        mvc.perform(get("/api/interviews/{id}", interviewId).header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentData").doesNotExist());

        mvc.perform(patch("/api/interviews/{id}/assessment", interviewId)
                        .header("Authorization", bearer(interviewer)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"assessmentData\":\"{\\\"finalAssessment\\\":\\\"Tentativa indevida\\\"}\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/interviews/{id}/workspace", interviewId).header("Authorization", bearer(adminB))
                        .header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/companies/invitations/accept")
                        .header("Authorization", bearer(interviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", invitationToken))))
                .andExpect(status().isConflict());

        MvcResult lifecycleInvitation = mvc.perform(post("/api/companies/invitations")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lifecycle@empresa-a.com\",\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isCreated()).andReturn();
        String lifecycleId = body(lifecycleInvitation).get("id").asText();
        String lifecyclePath = body(lifecycleInvitation).get("developmentPath").asText();
        String lifecycleToken = lifecyclePath.substring(lifecyclePath.lastIndexOf('/') + 1);

        mvc.perform(delete("/api/companies/invitations/{id}", lifecycleId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/companies/invitations/{id}", lifecycleId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/public/invitations/{token}", lifecycleToken)).andExpect(status().isNotFound());

        MvcResult replacement = mvc.perform(post("/api/companies/invitations")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resend@empresa-a.com\",\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isCreated()).andReturn();
        String replacementId = body(replacement).get("id").asText();
        String oldReplacementToken = body(replacement).get("developmentPath").asText().replaceFirst(".*/", "");
        MvcResult resent = mvc.perform(post("/api/companies/invitations/{id}/resend", replacementId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andReturn();
        String newReplacementToken = body(resent).get("developmentPath").asText().replaceFirst(".*/", "");
        mvc.perform(get("/api/public/invitations/{token}", oldReplacementToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/invitations/{token}", newReplacementToken)).andExpect(status().isOk());
        mvc.perform(get("/api/companies/invitations").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'resend@empresa-a.com')].status")
                        .value(containsInAnyOrder("PENDING", "REVOKED")));
        mvc.perform(get("/api/companies/invitations/page").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(4));

        mvc.perform(delete("/api/companies/members/{userId}", interviewerId)
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/companies/members/{userId}", interviewerId)
                        .header("Authorization", bearer(interviewer)).header("X-Company-Id", companyA))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/companies/members/{userId}", interviewerId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/job-positions").header("Authorization", bearer(interviewer))
                        .header("X-Company-Id", companyA).param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/companies/members/{userId}", adminAId)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
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

    private String createVacancy(String token, UUID companyId, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/job-positions").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}"
                                .formatted(title)))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("id").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private void verifyEmail(String accessToken) throws Exception {
        MvcResult requested = mvc.perform(post("/api/auth/email-verification/request")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isAccepted()).andReturn();
        String path = body(requested).get("developmentPath").asText();
        String token = path.substring(path.lastIndexOf('/') + 1);
        mvc.perform(post("/api/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", token))))
                .andExpect(status().isNoContent());
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
