package com.kologeski.interviewassistant.profile;

import com.fasterxml.jackson.databind.*;
import com.kologeski.interviewassistant.auth.RefreshTokenRepository;
import com.kologeski.interviewassistant.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserTechnicalSkillRepository skills;
    @Autowired InterviewAvailabilityRepository availability;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clean() {
        availability.deleteAll();
        skills.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
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
        addSlot(token, "MONDAY", "09:00", "11:00").andExpect(status().isCreated());
        addSlot(token, "MONDAY", "14:00", "16:00").andExpect(status().isCreated());
        addSlot(token, "MONDAY", "15:00", "17:00").andExpect(status().isConflict());
        addSlot(token, "TUESDAY", "14:00", "12:00").andExpect(status().isConflict());
        mvc.perform(get("/api/profile/availability").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void searchesByVacancyTechnologyAndAvailabilityWithoutExposingSensitiveData() throws Exception {
        String joao = register("João", "joao.search@example.com");
        addSkill(joao, "Java", "ADVANCED").andExpect(status().isCreated());
        addSkill(joao, "Spring Boot", "ADVANCED").andExpect(status().isCreated());
        addSlot(joao, "WEDNESDAY", "14:00", "17:00").andExpect(status().isCreated());
        String maria = register("Maria", "maria.search@example.com");
        addSkill(maria, "Java", "INTERMEDIATE").andExpect(status().isCreated());

        MvcResult vacancy = mvc.perform(post("/api/job-positions").header("Authorization", bearer(joao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Pleno\",\"seniority\":\"Pleno\",\"requirements\":[\"java\",\"Spring Boot\",\"SQL\"]}"))
                .andExpect(status().isOk()).andReturn();
        String vacancyId = body(vacancy).get("id").asText();

        mvc.perform(get("/api/interviewers").header("Authorization", bearer(joao))
                        .param("vacancyId", vacancyId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("João"))
                .andExpect(jsonPath("$[0].matchedCount").value(2))
                .andExpect(jsonPath("$[0].requirementCount").value(3))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/interviewers").header("Authorization", bearer(joao))
                        .param("technology", "JAVA").param("day", "WEDNESDAY").param("time", "15:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("João"));
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
    private ResultActions addSlot(String token, String day, String start, String end) throws Exception {
        return mvc.perform(post("/api/profile/availability").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}"
                        .formatted(day, start, end)));
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
    private String bearer(String token) { return "Bearer " + token; }
}
