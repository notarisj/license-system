package com.notaris.license_system.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.*;

public class LicenseValidator {
    private final ECPublicKey publicKey;
    private final byte[] aesKey;
    private static final Base64.Decoder URL_DEC = Base64.getUrlDecoder();
    private final ObjectMapper mapper = new ObjectMapper();
    private final RevocationChecker revocationChecker;

    public interface RevocationChecker {
        boolean isRevoked(String uuid);
    }

    public LicenseValidator(ECPublicKey publicKey, byte[] aesKey, RevocationChecker revocationChecker) {
        this.publicKey = publicKey;
        this.aesKey = aesKey;
        this.revocationChecker = revocationChecker;
    }

    public ValidationResult validate(String license, String hwFingerprint) {
        try {
            String[] parts = license.split("\\.");
            if (parts.length != 2)
                return ValidationResult.invalid(null, false);
            String payloadB64 = parts[0];
            String sigB64 = parts[1];
            byte[] sig = URL_DEC.decode(sigB64);
            if (!verify(payloadB64.getBytes(), sig))
                return ValidationResult.invalid(null, false);

            byte[] payloadBytes = URL_DEC.decode(payloadB64);
            byte[] json;
            if (aesKey != null && payloadBytes.length > 12) {
                byte[] nonce = Arrays.copyOfRange(payloadBytes, 0, 12);
                byte[] enc = Arrays.copyOfRange(payloadBytes, 12, payloadBytes.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
                json = cipher.doFinal(enc);
            } else {
                json = payloadBytes;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            String uuid = (String) data.get("uuid");
            boolean revoked = uuid != null && revocationChecker.isRevoked(uuid);
            Instant expiry = Instant.parse((String) data.get("expiry_date"));
            if (Instant.now().isAfter(expiry))
                return ValidationResult.invalid(data, revoked);
            String boundHw = (String) data.get("hw_fingerprint");
            if (boundHw != null) {
                if (hwFingerprint == null || !Objects.equals(boundHw, hwFingerprint))
                    return ValidationResult.invalid(data, revoked);
            } else if (hwFingerprint != null) {
                return ValidationResult.invalid(data, revoked);
            }
            return new ValidationResult(!revoked, data, revoked);
        } catch (Exception e) {
            return ValidationResult.invalid(null, false);
        }
    }

    private boolean verify(byte[] data, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("SHA512withECDSA");
        s.initVerify(publicKey);
        s.update(data);
        return s.verify(sig);
    }

    public static String hardwareFingerprint() {
        String os = System.getProperty("os.name");
        int cores = Runtime.getRuntime().availableProcessors();
        String arch = System.getProperty("os.arch");
        String basis = cores + "-" + os + "-" + arch;
        return sha256Hex(basis.getBytes());
    }

    private static String sha256Hex(byte[] input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : d)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record ValidationResult(boolean valid, Map<String, Object> data, boolean revoked) {
        public static ValidationResult invalid(Map<String, Object> data, boolean revoked) {
            return new ValidationResult(false, data, revoked);
        }
    }
}