package com.kologeski.interviewassistant.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kologeski.interviewassistant.PostgresqlTestDatabase;
import com.kologeski.interviewassistant.auth.JwtService;
import com.kologeski.interviewassistant.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PersonalAssessmentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    private String tokenA;
    private String tokenB;
    private Long ownerA;

    @BeforeEach void setup() {
        PostgresqlTestDatabase.reset(jdbc);
        var a = users.save(new User("A", "a@personal.test", "unused", Instant.now()));
        var b = users.save(new User("B", "b@personal.test", "unused", Instant.now()));
        ownerA = a.getId();
        tokenA = "Bearer " + jwt.generate(a);
        tokenB = "Bearer " + jwt.generate(b);
    }

    private String create() throws Exception {
        var result = mvc.perform(post("/api/assessments").header("Authorization", tokenA)
                .header("X-Company-Id", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" Java \",\"personName\":\" Pessoa \",\"context\":\"Promoção\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.companyId").doesNotExist()).andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test void createsWithoutMembershipAndIsolatesOwnersAndOrganizationalRoutes() throws Exception {
        String id = create();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_memberships", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM interviews WHERE id=?", Long.class, UUID.fromString(id))).isEqualTo(ownerA);
        mvc.perform(get("/api/assessments/{id}", id).header("Authorization", tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personName").value("Pessoa"));
        mvc.perform(get("/api/assessments/{id}", id).header("Authorization", tokenB)
                .header("X-Company-Id", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/assessments").header("Authorization", tokenB).param("ownerUserId", ownerA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/assessments").header("Authorization", tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        UUID company = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbc.update("INSERT INTO company_memberships(company_id,user_id,role,active,created_at,updated_at) VALUES (?,?,'COMPANY_ADMIN',TRUE,now(),now())", company, ownerA);
        mvc.perform(get("/api/interviews/{id}", id).header("Authorization", tokenA).header("X-Company-Id", company))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/interviews").header("Authorization", tokenA).header("X-Company-Id", company))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void databaseRejectsAmbiguousOrMissingScopeAndInvalidPersonalData() throws Exception {
        UUID id = UUID.fromString(create());
        assertThatThrownBy(() -> jdbc.update("UPDATE interviews SET owner_user_id=NULL WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE interviews SET company_id='00000000-0000-0000-0000-000000000001' WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE interviews SET title=' ' WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE interviews SET owner_user_id=9223372036854775807 WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void requiresAuthenticationAndValidatesInputAndPagination() throws Exception {
        mvc.perform(get("/api/assessments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assessments").header("Authorization", tokenA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"personName\":\"Pessoa\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/assessments").header("Authorization", tokenA).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test void organizationalRecordNeverAppearsInPersonalApiEvenForItsAuthor() throws Exception {
        UUID company = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID vacancy = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        UUID interview = UUID.randomUUID();
        jdbc.update("INSERT INTO job_vacancies(id,company_id,title,active,created_at,created_by,updated_at,updated_by) VALUES (?,?,'Vaga',TRUE,now(),?,now(),?)", vacancy, company, ownerA.toString(), ownerA.toString());
        jdbc.update("INSERT INTO candidates(id,company_id,display_name,normalized_name,created_at,created_by,updated_at,updated_by) VALUES (?,?,'Pessoa','pessoa',now(),?,now(),?)", candidate, company, ownerA.toString(), ownerA.toString());
        jdbc.update("INSERT INTO interviews(id,company_id,vacancy_id,candidate_id,candidate_name,vacancy_title_snapshot,status,created_at,created_by,updated_at,updated_by) VALUES (?,?,?,?,'Pessoa','Vaga','CREATED',now(),?,now(),?)", interview, company, vacancy, candidate, ownerA.toString(), ownerA.toString());
        mvc.perform(get("/api/assessments/{id}", interview).header("Authorization", tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assessments").header("Authorization", tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThatThrownBy(() -> jdbc.update("UPDATE interviews SET vacancy_id=NULL WHERE id=?", interview))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void managesValidatedCollectionLifecycleWithoutLosingDataOnConflict() throws Exception {
        String id = create();
        UUID criterionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        mvc.perform(get("/api/assessments/{id}/collection", id).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.document.schemaVersion").value(1))
                .andExpect(jsonPath("$.document.criteria").isEmpty())
                .andExpect(jsonPath("$.generationEligible").value(false));

        mvc.perform(patch("/api/assessments/{id}/collection/start", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));

        String document = """
                {"version":1,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"APIs REST","technology":"Spring Boot","score":4}],
                  "evidence":[{"id":"%s","criterionId":"%s","text":"Explicou controllers, services e repositories."}],
                  "notes":[{"id":"%s","text":"Observação geral permitida sem critério."}],
                  "strengths":[],"concerns":[]}}
                """.formatted(criterionId, evidenceId, criterionId, noteId);
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(document))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.document.criteria[0].score").value(4))
                .andExpect(jsonPath("$.generationEligible").value(false));

        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(document))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/assessments/{id}/collection", id).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.evidence[0].text")
                        .value("Explicou controllers, services e repositories."));
        mvc.perform(get("/api/assessments/{id}/collection", id).header("Authorization", tokenB))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/assessments/{id}/collection/complete", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.generationEligible").value(true));
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(document.replace("\"version\":1", "\"version\":3")))
                .andExpect(status().isConflict());
    }

    @Test void acceptsEmptyDraftButRejectsInvalidIdsReferencesScoresAndSchema() throws Exception {
        String id = create();
        mvc.perform(patch("/api/assessments/{id}/collection/start", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());

        String empty = """
                {"version":1,"document":{"schemaVersion":1,"criteria":[],"evidence":[],
                  "notes":[],"strengths":[],"concerns":[]}}
                """;
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(empty))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.generationEligible").value(false));

        UUID criterionId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        String invalidReference = """
                {"version":2,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"Java"}],
                  "evidence":[{"id":"%s","criterionId":"%s","text":"Evidência"}]}}
                """.formatted(criterionId, UUID.randomUUID(), UUID.randomUUID());
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidReference))
                .andExpect(status().isBadRequest());

        String duplicateIds = """
                {"version":2,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"Java"}],
                  "notes":[{"id":"%s","text":"Nota"}]}}
                """.formatted(duplicateId, duplicateId);
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateIds))
                .andExpect(status().isBadRequest());

        String invalidScore = """
                {"version":2,"document":{"schemaVersion":1,
                  "criteria":[{"id":"%s","name":"Java","score":6}]}}
                """.formatted(criterionId);
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidScore))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/assessments/{id}/collection", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"document\":{\"schemaVersion\":2}}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/assessments/{id}/collection/complete", id).header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generationEligible").value(false));
    }
}
