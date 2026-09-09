package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.PostgresqlTestDatabase;
import com.kologeski.interviewassistant.auth.JwtService;
import com.kologeski.interviewassistant.user.User;
import com.kologeski.interviewassistant.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PersonalReportIntegrationTest.FakeLlmConfiguration.class)
class PersonalReportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired CountingLlmClient llm;
    private final ObjectMapper json = new ObjectMapper();
    private String tokenA;
    private String tokenB;

    @BeforeEach void setup() {
        PostgresqlTestDatabase.reset(jdbc);
        User a = users.save(new User("A", "report-a@personal.test", "unused", Instant.now()));
        User b = users.save(new User("B", "report-b@personal.test", "unused", Instant.now()));
        tokenA = "Bearer " + jwt.generate(a);
        tokenB = "Bearer " + jwt.generate(b);
        llm.reset();
    }

    @Test void persistsOneDraftForConcurrentIdempotentRequestsAndKeepsItOwnerScoped() throws Exception {
        CompletedAssessment assessment = createCompletedAssessment("Pessoa confidencial");
        UUID requestKey = UUID.randomUUID();
        llm.delayMillis = 250;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> generate(assessment.id(), requestKey, ready, start));
            var second = executor.submit(() -> generate(assessment.id(), requestKey, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult one = first.get(10, TimeUnit.SECONDS);
            MvcResult two = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(one.getResponse().getStatus(), two.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 201);
            String firstId = json.readTree(one.getResponse().getContentAsString()).get("id").asText();
            String secondId = json.readTree(two.getResponse().getContentAsString()).get("id").asText();
            assertThat(secondId).isEqualTo(firstId);
        }

        assertThat(llm.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_reports", Integer.class)).isEqualTo(1);
        String context = jdbc.queryForObject("SELECT context_snapshot FROM assessment_reports", String.class);
        String original = jdbc.queryForObject("SELECT original_draft FROM assessment_reports", String.class);
        assertThat(context).doesNotContain("Pessoa confidencial", assessment.criterionId().toString(), "score");
        assertThat(original).doesNotContain("conclusion", "decision", "approval", "recommendation");

        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.draft.conclusion").doesNotExist());
        assertThat(llm.calls.get()).isEqualTo(1);

        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenB).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isNotFound());
        assertThat(llm.calls.get()).isEqualTo(1);
    }

    @Test void controlledProviderFailurePreservesTheCompletedCollectionAndAllowsRetry() throws Exception {
        CompletedAssessment assessment = createCompletedAssessment("Pessoa sintética");
        UUID requestKey = UUID.randomUUID();
        llm.failNext.set(true);

        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Ollama sintético indisponível."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_reports", Integer.class)).isZero();
        mvc.perform(get("/api/assessments/{id}/collection", assessment.id()).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.document.evidence[0].text").value("Explicou encapsulamento."));

        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(llm.calls.get()).isEqualTo(2);
    }

    @Test void keepsOriginalDraftWhileOwnerReviewsConcludesAndApprovesAnImmutableReport() throws Exception {
        CompletedAssessment assessment = createCompletedAssessment("Pessoa sintética");
        MvcResult generated = mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn();
        var report = json.readTree(generated.getResponse().getContentAsString());
        String originalText = report.get("originalDraft").get("context").get("text").asText();
        var revisedDraft = report.get("draft").deepCopy();
        ((tools.jackson.databind.node.ObjectNode) revisedDraft.get("context")).put("text", "Contexto revisado pelo entrevistador.");
        var review = json.createObjectNode();
        review.put("version", 0);
        review.set("draft", revisedDraft);
        review.put("humanConclusion", "Conclusão escrita exclusivamente pelo entrevistador.");
        review.put("reviewConfirmed", true);

        mvc.perform(get("/api/assessments/{id}/report", assessment.id()).header("Authorization", tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/assessments/{id}/report", assessment.id()).header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(review)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/assessments/{id}/report", assessment.id()).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(review)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.originalDraft.context.text").value(originalText))
                .andExpect(jsonPath("$.draft.context.text").value("Contexto revisado pelo entrevistador."))
                .andExpect(jsonPath("$.humanConclusion").value("Conclusão escrita exclusivamente pelo entrevistador."));

        UUID approvalKey = UUID.randomUUID();
        mvc.perform(post("/api/assessments/{id}/report/approve", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", approvalKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.approvedAt").isNotEmpty());
        mvc.perform(post("/api/assessments/{id}/report/approve", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", approvalKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(put("/api/assessments/{id}/report", assessment.id()).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(review)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT original_draft FROM assessment_reports", String.class))
                .contains(originalText).doesNotContain("Contexto revisado pelo entrevistador.");
    }

    @Test void deletingAnOwnedAssessmentAlsoDeletesItsDraftAndAnotherUserCannotDeleteIt() throws Exception {
        CompletedAssessment assessment = createCompletedAssessment("Pessoa sintética");
        mvc.perform(post("/api/assessments/{id}/report/generate", assessment.id())
                        .header("Authorization", tokenA).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/assessments/{id}", assessment.id()).header("Authorization", tokenB))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_reports", Integer.class)).isEqualTo(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/assessments/{id}", assessment.id()).header("Authorization", tokenA))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_reports", Integer.class)).isZero();
        mvc.perform(get("/api/assessments/{id}", assessment.id()).header("Authorization", tokenA))
                .andExpect(status().isNotFound());
    }

    private MvcResult generate(UUID assessmentId, UUID requestKey, CountDownLatch ready,
                               CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/assessments/{id}/report/generate", assessmentId)
                        .header("Authorization", tokenA).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":4}"))
                .andReturn();
    }

    private CompletedAssessment createCompletedAssessment(String personName) throws Exception {
        MvcResult created = mvc.perform(post("/api/assessments").header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java\",\"personName\":\"" + personName
                                + "\",\"context\":\"Avaliação sintética\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
        UUID criterionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        String preparation = """
                {"version":0,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"Java","score":null}],
                  "evidence":[],"notes":[],"strengths":[],"concerns":[]}}
                """.formatted(criterionId);
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(preparation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/assessments/{id}/collection/start", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        String document = """
                {"version":2,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"Java","score":5}],
                  "evidence":[{"id":"%s","criterionId":"%s","text":"Explicou encapsulamento."}],
                  "notes":[],"strengths":[],"concerns":[]}}
                """.formatted(criterionId, evidenceId, criterionId);
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(document))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(patch("/api/assessments/{id}/collection/complete", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4));
        return new CompletedAssessment(id, criterionId);
    }

    private record CompletedAssessment(UUID id, UUID criterionId) { }

    @TestConfiguration
    static class FakeLlmConfiguration {
        @Bean @Primary CountingLlmClient countingLlmClient() { return new CountingLlmClient(); }
    }

    static class CountingLlmClient implements LlmClient {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean failNext = new AtomicBoolean();
        volatile long delayMillis;

        void reset() { calls.set(0); failNext.set(false); delayMillis = 0; }

        @Override public LlmDraftResponse generateDraft(ReportPrompt prompt) {
            calls.incrementAndGet();
            if (failNext.compareAndSet(true, false)) {
                throw new LlmGenerationException(LlmGenerationException.Kind.UNAVAILABLE,
                        "Ollama sintético indisponível.");
            }
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            var context = new ReportDraft.Section("Contexto sintético.", List.of(), false);
            var evidenced = new ReportDraft.Section("Síntese sustentada.", List.of("evidence-1"), false);
            var insufficient = new ReportDraft.Section("Sem dados suficientes.", List.of(), true);
            return new LlmDraftResponse(new ReportDraft(
                    context, evidenced, insufficient, insufficient, evidenced), "stub-local-model");
        }
    }
}
