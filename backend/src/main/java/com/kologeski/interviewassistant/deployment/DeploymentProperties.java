package com.kologeski.interviewassistant.deployment;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.deployment")
public class DeploymentProperties {
    private DeploymentMode mode = DeploymentMode.SAAS;
    private Boolean publicSignupEnabled;
    private Boolean multiTenantEnabled;
    private Boolean billingEnabled;

    @PostConstruct
    void validate() {
        if (mode == DeploymentMode.ON_PREMISE
                && (Boolean.TRUE.equals(publicSignupEnabled)
                || Boolean.TRUE.equals(multiTenantEnabled)
                || Boolean.TRUE.equals(billingEnabled))) {
            throw new IllegalStateException(
                    "ON_PREMISE does not support enabling public signup, multi-tenancy or billing.");
        }
    }

    public DeploymentMode getMode() { return mode; }
    public void setMode(DeploymentMode mode) { this.mode = mode; }
    public Boolean getPublicSignupEnabled() { return publicSignupEnabled; }
    public void setPublicSignupEnabled(Boolean value) { this.publicSignupEnabled = value; }
    public Boolean getMultiTenantEnabled() { return multiTenantEnabled; }
    public void setMultiTenantEnabled(Boolean value) { this.multiTenantEnabled = value; }
    public Boolean getBillingEnabled() { return billingEnabled; }
    public void setBillingEnabled(Boolean value) { this.billingEnabled = value; }

    public boolean publicSignupEnabled() {
        return publicSignupEnabled != null ? publicSignupEnabled : mode == DeploymentMode.SAAS;
    }

    public boolean multiTenantEnabled() {
        return multiTenantEnabled != null ? multiTenantEnabled : mode == DeploymentMode.SAAS;
    }

    public boolean billingEnabled() {
        return billingEnabled != null ? billingEnabled : mode == DeploymentMode.SAAS;
    }
}
