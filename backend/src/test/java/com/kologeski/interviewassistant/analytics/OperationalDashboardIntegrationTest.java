package com.kologeski.interviewassistant.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kologeski.interviewassistant.PostgresqlTestDatabase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OperationalDashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() { PostgresqlTestDatabase.reset(jdbc); }

    @Test
    void aggregatesOperationalMetricsWithoutPiiAndNeverCrossesTenantBoundaries() throws Exception {
        String adminA = register("analytics-a@empresa.test");
        String adminB = register("analytics-b@empresa.test");
        UUID companyA = createCompany(adminA, "Analytics A");
        UUID companyB = createCompany(adminB, "Analytics B");
        String vacancyA = createVacancy(adminA, companyA, "Backend A");
        String vacancyB = createVacancy(adminB, companyB, "Backend B");

        createInterview(adminA, companyA, vacancyA, "Candidata confidencial 1", true);
        createInterview(adminA, companyA, vacancyA, "Candidato confidencial 2", true);
        createInterview(adminA, companyA, vacancyA, "Candidata confidencial 3", false);
        createInterview(adminB, companyB, vacancyB, "Pessoa de outro tenant 1", true);
        createInterview(adminB, companyB, vacancyB, "Pessoa de outro tenant 2", false);

        MvcResult companyAResult = mvc.perform(get("/api/analytics/operational")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.inProgress").value(1))
                .andExpect(jsonPath("$.summary.completed").value(2))
                .andExpect(jsonPath("$.summary.completionRate").value(66.67))
                .andExpect(jsonPath("$.byVacancy.totalElements").value(1))
                .andExpect(jsonPath("$.byVacancy.content[0].vacancyTitle").value("Backend A"))
                .andExpect(jsonPath("$.byInterviewer.content[0].interviewerCode").isNotEmpty())
                .andReturn();
        String payload = companyAResult.getResponse().getContentAsString();
        assertThat(payload).doesNotContain("Candidata confidencial", "Pessoa de outro tenant", "assessmentData", "email");

        mvc.perform(get("/api/analytics/operational").param("status", "COMPLETED")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(2));
        mvc.perform(get("/api/analytics/operational").param("vacancyId", vacancyB)
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(0));
        mvc.perform(get("/api/analytics/operational")
                        .header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.byVacancy.content[0].vacancyTitle").value("Backend B"));
        mvc.perform(get("/api/analytics/operational").param("from", "2024-01-02").param("to", "2024-01-01")
                        .header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isBadRequest());
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Analytics\",\"email\":\"%s\",\"password\":\"123456\",\"rememberMe\":false}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return body(result).get("accessToken").asText();
    }
    private UUID createCompany(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/companies").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }
    private String createVacancy(String token, UUID companyId, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/job-positions").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}".formatted(title)))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("id").asText();
    }
    private void createInterview(String token, UUID companyId, String vacancyId, String candidate, boolean complete) throws Exception {
        MvcResult created = mvc.perform(post("/api/interviews").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":\"%s\",\"candidateName\":\"%s\"}".formatted(vacancyId, candidate)))
                .andExpect(status().isOk()).andReturn();
        String interviewId = body(created).get("id").asText();
        if (complete) mvc.perform(patch("/api/interviews/{id}/complete", interviewId)
                        .header("Authorization", bearer(token)).header("X-Company-Id", companyId))
                .andExpect(status().isOk());
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String bearer(String token) { return "Bearer " + token; }
}
