package com.kologeski.interviewassistant.enterpriseidentity;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EnterpriseIdentityReadinessService {
    private final EnterpriseIdentityProperties properties;
    private final List<EnterpriseIdentityProviderPort> providers;

    public EnterpriseIdentityReadinessService(EnterpriseIdentityProperties properties,
                                              List<EnterpriseIdentityProviderPort> providers) {
        this.properties = properties;
        this.providers = List.copyOf(providers);
    }

    public Readiness readiness() {
        List<Blocker> blockers = new ArrayList<>();
        if (!properties.isEnabled()) blockers.add(new Blocker("FEATURE_DISABLED", "A autenticação corporativa ainda não foi habilitada."));
        if (!properties.isProviderApproved()) blockers.add(new Blocker("PROVIDER_DECISION_PENDING", "O provedor de identidade ainda precisa ser aprovado."));
        if (properties.getProtocol() == null) blockers.add(new Blocker("PROTOCOL_NOT_SELECTED", "Escolha OIDC ou SAML após validar o provedor."));
        if (properties.getVerifiedDomains().stream().noneMatch(value -> value != null && !value.isBlank())) blockers.add(new Blocker("DOMAIN_NOT_VERIFIED", "Nenhum domínio corporativo foi verificado."));
        if (properties.getIssuer() == null || properties.getIssuer().isBlank()
                || properties.getAudience() == null || properties.getAudience().isBlank()) {
            blockers.add(new Blocker("CLAIM_CONTRACT_INCOMPLETE", "Issuer e audience ainda não foram validados com o provedor."));
        }
        if (!properties.isRecoveryPolicyApproved()) blockers.add(new Blocker("RECOVERY_POLICY_PENDING", "A política de recuperação e acesso de emergência ainda precisa ser aprovada."));
        if (!adapterAvailable()) blockers.add(new Blocker("ADAPTER_UNAVAILABLE", "Não existe um adaptador de identidade validado para esta configuração."));
        return new Readiness(blockers.isEmpty(), properties.getProtocol(), List.copyOf(blockers));
    }

    public void requireReady() {
        Readiness readiness = readiness();
        if (!readiness.ready()) throw new EnterpriseIdentityUnavailableException(readiness.blockers());
    }

    private boolean adapterAvailable() {
        if (properties.getProviderKey() == null || properties.getProtocol() == null) return false;
        return providers.stream().anyMatch(provider -> provider.configured()
                && properties.getProviderKey().equals(provider.providerKey())
                && properties.getProtocol() == provider.protocol());
    }

    public record Blocker(String code, String message) { }
    public record Readiness(boolean ready, EnterpriseIdentityProtocol protocol, List<Blocker> blockers) { }
    public static final class EnterpriseIdentityUnavailableException extends RuntimeException {
        private final List<Blocker> blockers;
        EnterpriseIdentityUnavailableException(List<Blocker> blockers) {
            super("A autenticação corporativa não está pronta para ativação.");
            this.blockers = List.copyOf(blockers);
        }
        public List<Blocker> blockers() { return blockers; }
    }
}
