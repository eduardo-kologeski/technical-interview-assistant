package com.kologeski.interviewassistant.enterpriseidentity;

/** Provider adapter boundary. A real adapter must verify protocol signatures before producing an assertion. */
public interface EnterpriseIdentityProviderPort {
    String providerKey();
    EnterpriseIdentityProtocol protocol();
    boolean configured();
}
