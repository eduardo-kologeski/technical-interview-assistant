package com.kologeski.interviewassistant.deployment;

import com.kologeski.interviewassistant.company.*;
import com.kologeski.interviewassistant.user.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class InstallationSetupService {
    static final UUID LEGACY_COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final DeploymentProperties deployment;
    private final InstallationStateRepository states;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public InstallationSetupService(DeploymentProperties deployment, InstallationStateRepository states,
            UserRepository users, CompanyRepository companies, CompanyMembershipRepository memberships,
            PasswordEncoder passwords, Clock clock) {
        this.deployment = deployment; this.states = states; this.users = users; this.companies = companies;
        this.memberships = memberships; this.passwords = passwords; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean setupRequired() {
        return deployment.getMode() == DeploymentMode.ON_PREMISE
                && states.findById((short) 1).map(state -> !state.setupCompleted()).orElse(true);
    }

    @Transactional
    public void setup(SetupCommand command) {
        if (deployment.getMode() != DeploymentMode.ON_PREMISE) throw new SetupUnavailableException();
        InstallationState state = states.lockSingleton().orElseGet(() -> states.save(new InstallationState((short) 1)));
        if (state.setupCompleted()) throw new SetupUnavailableException();
        if (users.count() != 0) throw new UnsafeInstallationStateException();
        Company company = companies.findById(LEGACY_COMPANY_ID).orElseThrow(UnsafeInstallationStateException::new);
        if (companies.count() != 1) throw new UnsafeInstallationStateException();

        String email = command.adminEmail().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new UnsafeInstallationStateException();
        Instant now = clock.instant();
        User admin = users.save(new User(command.adminName().trim(), email,
                passwords.encode(command.password()), now));
        admin.verifyEmail(now);
        company.configure(command.organizationName(), "on-premise-organization", admin.getId(), now);
        memberships.save(new CompanyMembership(company.id(), admin.getId(), CompanyRole.COMPANY_ADMIN, now));
        state.complete(now);
    }

    public record SetupCommand(String organizationName, String adminName, String adminEmail, String password) { }
    public static final class SetupUnavailableException extends RuntimeException {
        public SetupUnavailableException() { super("A configuração inicial não está disponível."); }
    }
    public static final class UnsafeInstallationStateException extends RuntimeException {
        public UnsafeInstallationStateException() { super("A instalação não está em um estado seguro para configuração inicial."); }
    }
}
