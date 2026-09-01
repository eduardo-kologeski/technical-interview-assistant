package com.kologeski.interviewassistant.deployment;

import com.kologeski.interviewassistant.company.CompanyMembershipRepository;
import com.kologeski.interviewassistant.company.CompanyRepository;
import com.kologeski.interviewassistant.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.deployment.mode=ON_PREMISE"
})
@AutoConfigureMockMvc
@Sql(statements = {
        "TRUNCATE TABLE users CASCADE",
        "INSERT INTO companies(id,name,slug,created_at,updated_at) VALUES " +
                "('00000000-0000-0000-0000-000000000001','Ambiente legado','ambiente-legado',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET name='Ambiente legado', slug='ambiente-legado', updated_at=CURRENT_TIMESTAMP",
        "UPDATE installation_state SET setup_completed=FALSE, completed_at=NULL WHERE id=1"
})
class InstallationSetupIntegrationTest {
    private static final String REQUEST = """
            {"organizationName":"Acme","adminName":"Admin Inicial","adminEmail":"admin@acme.test","password":"strong-password"}
            """;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CompanyMembershipRepository memberships;

    @Test
    void serializesFirstAdminCreationAndAllowsLogin() throws Exception {
        mvc.perform(get("/api/public/setup"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setupRequired").value(true));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> attempt = () -> {
                start.await();
                return mvc.perform(post("/api/public/setup").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(attempt);
            Future<Integer> second = executor.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 409);
        }

        assertThat(users.count()).isEqualTo(1);
        assertThat(companies.count()).isEqualTo(1);
        assertThat(memberships.count()).isEqualTo(1);
        assertThat(companies.findById(InstallationSetupService.LEGACY_COMPANY_ID).orElseThrow().name()).isEqualTo("Acme");

        mvc.perform(get("/api/public/setup"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setupRequired").value(false));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"admin@acme.test","password":"strong-password","rememberMe":false}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
