package com.kologeski.interviewassistant.deployment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/deployment")
public class DeploymentController {
    private final DeploymentProperties deployment;

    public DeploymentController(DeploymentProperties deployment) { this.deployment = deployment; }

    @GetMapping
    DeploymentConfiguration configuration() {
        return new DeploymentConfiguration(deployment.getMode(), deployment.publicSignupEnabled(),
                deployment.multiTenantEnabled(), deployment.billingEnabled());
    }

    public record DeploymentConfiguration(DeploymentMode mode, boolean publicSignupEnabled,
                                          boolean multiTenantEnabled, boolean billingEnabled) { }
}
