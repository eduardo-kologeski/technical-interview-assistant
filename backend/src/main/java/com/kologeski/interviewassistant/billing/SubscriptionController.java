package com.kologeski.interviewassistant.billing;

import com.kologeski.interviewassistant.deployment.DeploymentProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    private final EntitlementService entitlements;
    private final DeploymentProperties deployment;
    public SubscriptionController(EntitlementService entitlements, DeploymentProperties deployment) {
        this.entitlements = entitlements; this.deployment = deployment;
    }
    @GetMapping public ResponseEntity<EntitlementService.SubscriptionSnapshot> current() {
        return deployment.billingEnabled() ? ResponseEntity.ok(entitlements.current()) : ResponseEntity.noContent().build();
    }
}
