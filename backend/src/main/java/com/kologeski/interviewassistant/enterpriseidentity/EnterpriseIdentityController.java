package com.kologeski.interviewassistant.enterpriseidentity;

import com.kologeski.interviewassistant.company.CompanyAuthorizationPolicy;
import com.kologeski.interviewassistant.company.CompanyCapability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enterprise-identity")
public class EnterpriseIdentityController {
    private final CompanyAuthorizationPolicy authorization;
    private final EnterpriseIdentityReadinessService readiness;

    public EnterpriseIdentityController(CompanyAuthorizationPolicy authorization,
                                        EnterpriseIdentityReadinessService readiness) {
        this.authorization = authorization;
        this.readiness = readiness;
    }

    @GetMapping("/readiness")
    EnterpriseIdentityReadinessService.Readiness readiness() {
        authorization.require(CompanyCapability.MANAGE_ENTERPRISE_IDENTITY);
        return readiness.readiness();
    }
}
