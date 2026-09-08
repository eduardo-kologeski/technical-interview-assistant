package com.kologeski.interviewassistant.billing;

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
class SubscriptionEntitlementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() { PostgresqlTestDatabase.reset(jdbc); }

    @Test
    void provisionsTrialAndEnforcesTenantQuotasWhileKeepingReadsAvailable() throws Exception {
        String adminA = register("billing-a@empresa.test");
        String adminB = register("billing-b@empresa.test");
        UUID companyA = createCompany(adminA, "Billing A");
        UUID companyB = createCompany(adminB, "Billing B");

        MvcResult starter = mvc.perform(get("/api/subscription").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planCode").value("STARTER"))
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.trialDaysRemaining").isNumber())
                .andExpect(jsonPath("$.usage.activeVacanciesLimit").value(25)).andReturn();
        assertThat(starter.getResponse().getContentAsString())
                .doesNotContain("email", "candidate", "payment", "card", "invoice");

        installLimitedPlan(companyA);
        String vacancyA = createVacancy(adminA, companyA, "Unica vaga", 200);
        createVacancy(adminA, companyA, "Vaga excedente", 409);
        createVacancy(adminB, companyB, "Vaga B 1", 200);
        createVacancy(adminB, companyB, "Vaga B 2", 200);

        createInterview(adminA, companyA, vacancyA, "Pessoa protegida", 200);
        createInterview(adminA, companyA, vacancyA, "Outra pessoa", 409);
        mvc.perform(post("/api/companies/invitations").header("Authorization", bearer(adminA))
                        .header("X-Company-Id", companyA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"novo@empresa.test\",\"role\":\"INTERVIEWER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Limite")));

        jdbc.update("UPDATE saas_subscriptions SET trial_ends_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE company_id=?", companyA);
        mvc.perform(get("/api/subscription").header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trialDaysRemaining").value(0));
        mvc.perform(get("/api/job-positions").header("Authorization", bearer(adminA)).header("X-Company-Id", companyA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].title").value("Unica vaga"));
        createVacancy(adminA, companyA, "Bloqueada após avaliação", 409);

        mvc.perform(get("/api/subscription").header("Authorization", bearer(adminB)).header("X-Company-Id", companyB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planCode").value("STARTER"))
                .andExpect(jsonPath("$.usage.activeVacancies").value(2));
    }

    private void installLimitedPlan(UUID companyId) {
        jdbc.update("INSERT INTO saas_plans(code,name,trial_days,max_active_vacancies,max_members,max_monthly_interviews,active,created_at) VALUES ('TEST_LIMITED','Teste limitado',30,1,1,1,TRUE,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING");
        for (String feature : new String[]{"vacancy.create", "interview.create", "member.invite", "data.export"})
            jdbc.update("INSERT INTO saas_entitlements(plan_code,feature_key,enabled) VALUES ('TEST_LIMITED',?,TRUE) ON CONFLICT DO NOTHING", feature);
        jdbc.update("UPDATE saas_subscriptions SET plan_code='TEST_LIMITED' WHERE company_id=?", companyId);
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Billing\",\"email\":\"%s\",\"password\":\"123456\",\"rememberMe\":false}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        return body(result).get("accessToken").asText();
    }
    private UUID createCompany(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/companies").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(body(result).get("id").asText());
    }
    private String createVacancy(String token, UUID companyId, String title, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/job-positions").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"seniority\":\"Pleno\",\"requirements\":[\"Java\"]}".formatted(title)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? body(result).get("id").asText() : null;
    }
    private void createInterview(String token, UUID companyId, String vacancyId, String candidate, int expectedStatus) throws Exception {
        mvc.perform(post("/api/interviews").header("Authorization", bearer(token))
                        .header("X-Company-Id", companyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionId\":\"%s\",\"candidateName\":\"%s\"}".formatted(vacancyId, candidate)))
                .andExpect(status().is(expectedStatus));
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String bearer(String token) { return "Bearer " + token; }
}
