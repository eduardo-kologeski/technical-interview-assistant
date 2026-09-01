package com.kologeski.interviewassistant.profile;

import com.fasterxml.jackson.databind.*;
import com.kologeski.interviewassistant.auth.RefreshTokenRepository;
import com.kologeski.interviewassistant.auth.IdentityTokenRepository;
import com.kologeski.interviewassistant.user.UserRepository;
import com.kologeski.interviewassistant.company.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired IdentityTokenRepository identityTokens;
    @Autowired UserTechnicalSkillRepository skills;
    @Autowired InterviewAvailabilityRepository availability;
    @Autowired Clock clock;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMembershipRepository memberships;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() {
        com.kologeski.interviewassistant.PostgresqlTestDatabase.reset(jdbc);
    }

    @Test void profileRequiresAuthenticationAndOnlyUpdatesCurrentUser() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        String first = register("João", "joao@example.com");
        String second = register("Maria", "maria@example.com");

        mvc.perform(put("/api/profile").header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"João Silva\",\"email\":\"joao.silva@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("João Silva"));
        mvc.perform(get("/api/profile").header("Authorization", bearer(second)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Maria"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void managesSkillsAndRejectsNormalizedDuplicates() throws Exception {
        String token = register("João", "skills@example.com");
        MvcResult created = addSkill(token, "Java", "ADVANCED").andExpect(status().isCreated()).andReturn();
        long id = body(created).get("id").asLong();
        addSkill(token, " java ", "BEGINNER").andExpect(status().isConflict());
        mvc.perform(put("/api/profile/technical-skills/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java\",\"proficiencyLevel\":\"INTERMEDIATE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proficiencyLevel").value("INTERMEDIATE"));
        mvc.perform(delete("/api/profile/technical-skills/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test void managesMultiplePeriodsAndRejectsInvalidOrOverlappingAvailability() throws Exception {
        String token = register("João", "availability@example.com");
        LocalDate firstDate = LocalDate.now().plusDays(7);
        LocalDate otherWeek = firstDate.plusWeeks(1);
        addSlot(token, firstDate, "13:00", "14:00").andExpect(status().isCreated());
        addSlot(token, firstDate, "08:00", "09:00").andExpect(status().isCreated());
        addSlot(token, firstDate, "09:00", "10:00").andExpect(status().isCreated());
        addSlot(token, firstDate, "08:30", "10:00").andExpect(status().isConflict());
        addSlot(token, firstDate, "14:00", "14:00").andExpect(status().isConflict());
        addSlot(token, otherWeek, "14:00", "16:00").andExpect(status().isCreated());
        addSlot(token, LocalDate.now().minusDays(1), "09:00", "10:00").andExpect(status().isConflict());
        mvc.perform(get("/api/profile/availability").header("Authorization", bearer(token))
                        .param("from", firstDate.toString()).param("to", firstDate.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("09:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("13:00:00"));
    }

    @Test void availabilityValidationUsesTheConfiguredLocalTimeZone() {
        Assertions.assertEquals(ZoneId.of("America/Sao_Paulo"), clock.getZone());
    }

    @Test void createsThreeAvailabilityIntervalsAtomicallyAndPreservesThemOnReload() throws Exception {
        String token = register("João", "availability.batch@example.com");
        LocalDate date = LocalDate.now().plusDays(8);
        mvc.perform(post("/api/profile/availability/batch").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"date":"%s","startTime":"08:00","endTime":"09:00"},
                                 {"date":"%s","startTime":"16:30","endTime":"18:00"},
                                 {"date":"%s","startTime":"19:00","endTime":"20:00"}]
                                """.formatted(date, date, date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(post("/api/profile/availability/batch").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"date":"%s","startTime":"10:00","endTime":"11:00"},
                                 {"date":"%s","startTime":"10:30","endTime":"11:30"}]
                                """.formatted(date, date)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/profile/availability").header("Authorization", bearer(token))
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("16:30:00"))
                .andExpect(jsonPath("$[2].startTime").value("19:00:00"));
    }

    @Test void searchesByVacancyTechnologyAndAvailabilityWithoutExposingSensitiveData() throws Exception {
        String joao = register("João", "joao.search@example.com");
        addSkill(joao, "Java", "ADVANCED").andExpect(status().isCreated());
        addSkill(joao, "Spring Boot", "ADVANCED").andExpect(status().isCreated());
        LocalDate interviewDate = LocalDate.now().plusDays(5);
        addSlot(joao, interviewDate, "14:00", "17:00").andExpect(status().isCreated());
        String maria = register("Maria", "maria.search@example.com");
        addSkill(maria, "Java", "INTERMEDIATE").andExpect(status().isCreated());
        var joaoUser = users.findByEmail("joao.search@example.com").orElseThrow();
        var mariaUser = users.findByEmail("maria.search@example.com").orElseThrow();
        var joaoCompany = companies.findAll().stream().filter(item -> joaoUser.getId().equals(item.createdByUserId())).findFirst().orElseThrow();
        memberships.save(new CompanyMembership(joaoCompany.id(), mariaUser.getId(), CompanyRole.INTERVIEWER, Instant.now()));

        MvcResult vacancy = mvc.perform(post("/api/job-positions").header("Authorization", bearer(joao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Pleno\",\"seniority\":\"Pleno\",\"requirements\":[\"java\",\"Spring Boot\",\"SQL\"]}"))
                .andExpect(status().isOk()).andReturn();
        String vacancyId = body(vacancy).get("id").asText();

        mvc.perform(get("/api/interviewers").header("Authorization", bearer(joao))
                        .param("vacancyId", vacancyId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("João"))
                .andExpect(jsonPath("$.content[0].matchedCount").value(2))
                .andExpect(jsonPath("$.content[0].requirementCount").value(3))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/interviewers").header("Authorization", bearer(joao))
                        .param("technology", "JAVA").param("date", interviewDate.toString()).param("time", "15:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("João"));
        mvc.perform(get("/api/interviewers").header("Authorization", bearer(joao))
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    private String register(String name, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"123456\",\"rememberMe\":false}"
                                .formatted(name, email))).andExpect(status().isCreated()).andReturn();
        return body(result).get("accessToken").asText();
    }
    private ResultActions addSkill(String token, String name, String level) throws Exception {
        return mvc.perform(post("/api/profile/technical-skills").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"proficiencyLevel\":\"%s\"}".formatted(name, level)));
    }
    private ResultActions addSlot(String token, LocalDate date, String start, String end) throws Exception {
        return mvc.perform(post("/api/profile/availability").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}"
                        .formatted(date, start, end)));
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
    private String bearer(String token) { return "Bearer " + token; }
}
