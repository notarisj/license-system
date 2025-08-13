package com.notaris.license_system.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class LicenseGenerator {
    private final ECPrivateKey privateKey;
    private final byte[] aesKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Base64.Encoder URL_B64 = Base64.getUrlEncoder().withoutPadding();

    public LicenseGenerator(ECPrivateKey privateKey, byte[] aesKey) {
        this.privateKey = privateKey;
        this.aesKey = aesKey;
    }

    public String generate(String customerId,
            int daysValid,
            String hwFingerprint,
            Map<String, Object> metadata,
            Integer usageLimit,
            String version) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", version);
            payload.put("customer_id", customerId);
            payload.put("issue_date", Instant.now().toString());
            payload.put("expiry_date", Instant.now().plus(daysValid, ChronoUnit.DAYS).toString());
            payload.put("uuid", UUID.randomUUID().toString());
            payload.put("hw_fingerprint", hwFingerprint);
            payload.put("metadata", metadata != null ? metadata : new LinkedHashMap<>());
            payload.put("usage_limit", usageLimit);

            byte[] json = mapper.writeValueAsBytes(payload);
            String payloadB64;
            if (aesKey != null) {
                byte[] nonce = new byte[12];
                new Random().nextBytes(nonce);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
                byte[] enc = cipher.doFinal(json);
                byte[] combined = new byte[nonce.length + enc.length];
                System.arraycopy(nonce, 0, combined, 0, nonce.length);
                System.arraycopy(enc, 0, combined, nonce.length, enc.length);
                payloadB64 = URL_B64.encodeToString(combined);
            } else {
                payloadB64 = URL_B64.encodeToString(json);
            }
            byte[] sigBytes = sign(payloadB64.getBytes());
            String sigB64 = URL_B64.encodeToString(sigBytes);
            return payloadB64 + "." + sigB64;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sign(byte[] data) throws Exception {
        Signature s = Signature.getInstance("SHA512withECDSA");
        s.initSign(privateKey);
        s.update(data);
        return s.sign();
    }
}