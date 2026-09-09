package com.kologeski.interviewassistant.user;

import com.kologeski.interviewassistant.PostgresqlTestDatabase;
import com.kologeski.interviewassistant.auth.JwtService;
import com.kologeski.interviewassistant.auth.RefreshTokenService;
import com.kologeski.interviewassistant.interview.Interview;
import com.kologeski.interviewassistant.interview.InterviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InterviewRepository interviews;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired RefreshTokenService refreshTokens;
    private User owner;
    private User other;
    private String token;

    @BeforeEach void setup() {
        PostgresqlTestDatabase.reset(jdbc);
        Instant now = Instant.now();
        owner = users.save(new User("Owner", "owner-delete@test.local", passwords.encode("current-password"), now));
        other = users.save(new User("Other", "other-delete@test.local", passwords.encode("other-password"), now));
        token = "Bearer " + jwt.generate(owner);
        interviews.save(Interview.personal(owner.getId(), "Excluir", "Pessoa A", null, now));
        interviews.save(Interview.personal(other.getId(), "Preservar", "Pessoa B", null, now));
        refreshTokens.create(owner, new RefreshTokenService.Metadata("browser", "test", "127.0.0.1"));
        jdbc.update("INSERT INTO company_memberships(company_id,user_id,role,active,created_at,updated_at) " +
                "VALUES ('00000000-0000-0000-0000-000000000001',?,'INTERVIEWER',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", owner.getId());
    }

    @Test void requiresCurrentPasswordThenDeletesPersonalDataAndAnonymizesOrganizationIdentity() throws Exception {
        mvc.perform(delete("/api/users/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(interviews.findByOwnerUserId(owner.getId())).hasSize(1);

        mvc.perform(delete("/api/users/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"current-password\"}"))
                .andExpect(status().isNoContent());

        assertThat(interviews.findByOwnerUserId(owner.getId())).isEmpty();
        assertThat(interviews.findByOwnerUserId(other.getId())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Integer.class, owner.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM company_memberships WHERE user_id=?", Boolean.class, owner.getId())).isFalse();
        User anonymized = users.findById(owner.getId()).orElseThrow();
        assertThat(anonymized.isDeleted()).isTrue();
        assertThat(anonymized.getName()).isEqualTo("Usuário excluído");
        assertThat(anonymized.getEmail()).endsWith("@invalid.local").doesNotContain("owner-delete");
        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
}
