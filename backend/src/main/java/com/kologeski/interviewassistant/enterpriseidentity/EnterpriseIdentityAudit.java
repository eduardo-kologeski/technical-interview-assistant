package com.kologeski.interviewassistant.enterpriseidentity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class EnterpriseIdentityAudit {
    private static final Logger log = LoggerFactory.getLogger(EnterpriseIdentityAudit.class);

    public void record(String action, String providerKey, String tenantReference, String subject) {
        log.info("event=enterprise_identity action={} provider={} tenant={} subjectFingerprint={}",
                safe(action), safe(providerKey), safe(tenantReference), fingerprint(subject));
    }

    String fingerprint(String value) {
        if (value == null || value.isBlank()) return "unknown";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível.", impossible);
        }
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,100}")) return "unknown";
        return value;
    }
}
