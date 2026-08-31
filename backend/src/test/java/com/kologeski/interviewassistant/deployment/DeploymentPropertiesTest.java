package com.kologeski.interviewassistant.deployment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentPropertiesTest {
    @Test
    void derivesSaasDefaults() {
        var properties = new DeploymentProperties();
        assertThat(properties.getMode()).isEqualTo(DeploymentMode.SAAS);
        assertThat(properties.publicSignupEnabled()).isTrue();
        assertThat(properties.multiTenantEnabled()).isTrue();
        assertThat(properties.billingEnabled()).isTrue();
    }

    @Test
    void derivesSafeOnPremiseDefaults() {
        var properties = new DeploymentProperties();
        properties.setMode(DeploymentMode.ON_PREMISE);
        properties.validate();
        assertThat(properties.publicSignupEnabled()).isFalse();
        assertThat(properties.multiTenantEnabled()).isFalse();
        assertThat(properties.billingEnabled()).isFalse();
    }

    @Test
    void rejectsInvalidOnPremiseCapabilities() {
        var properties = new DeploymentProperties();
        properties.setMode(DeploymentMode.ON_PREMISE);
        properties.setPublicSignupEnabled(true);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
