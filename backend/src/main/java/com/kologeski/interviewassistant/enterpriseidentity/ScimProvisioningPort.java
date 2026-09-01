package com.kologeski.interviewassistant.enterpriseidentity;

import java.util.Set;

/** Provider-neutral boundary; no SCIM endpoint or credential is enabled by this contract. */
public interface ScimProvisioningPort {
    ProvisioningResult provision(ProvisioningCommand command);
    ProvisioningResult deprovision(String tenantReference, String externalId);

    record ProvisioningCommand(String tenantReference, String externalId, String email, String displayName,
                               Set<String> groups, boolean active) { }
    record ProvisioningResult(String externalId, Status status) {
        public enum Status { CREATED, UPDATED, DEACTIVATED, UNCHANGED }
    }
}
