package com.kologeski.interviewassistant.enterpriseidentity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.enterprise-identity")
public class EnterpriseIdentityProperties {
    private boolean enabled;
    private boolean providerApproved;
    private boolean recoveryPolicyApproved;
    private EnterpriseIdentityProtocol protocol;
    private String providerKey;
    private String issuer;
    private String audience;
    private List<String> verifiedDomains = new ArrayList<>();
    private ClaimMapping claims = new ClaimMapping();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isProviderApproved() { return providerApproved; }
    public void setProviderApproved(boolean providerApproved) { this.providerApproved = providerApproved; }
    public boolean isRecoveryPolicyApproved() { return recoveryPolicyApproved; }
    public void setRecoveryPolicyApproved(boolean recoveryPolicyApproved) { this.recoveryPolicyApproved = recoveryPolicyApproved; }
    public EnterpriseIdentityProtocol getProtocol() { return protocol; }
    public void setProtocol(EnterpriseIdentityProtocol protocol) { this.protocol = protocol; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public List<String> getVerifiedDomains() { return verifiedDomains; }
    public void setVerifiedDomains(List<String> verifiedDomains) {
        this.verifiedDomains = verifiedDomains == null ? new ArrayList<>() : new ArrayList<>(verifiedDomains);
    }
    public ClaimMapping getClaims() { return claims; }
    public void setClaims(ClaimMapping claims) { this.claims = claims == null ? new ClaimMapping() : claims; }

    public static class ClaimMapping {
        private String subject = "sub";
        private String email = "email";
        private String emailVerified = "email_verified";
        private String groups = "groups";

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getEmailVerified() { return emailVerified; }
        public void setEmailVerified(String emailVerified) { this.emailVerified = emailVerified; }
        public String getGroups() { return groups; }
        public void setGroups(String groups) { this.groups = groups; }
    }
}
