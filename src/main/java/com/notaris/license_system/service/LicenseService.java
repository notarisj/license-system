package com.notaris.license_system.service;

import com.notaris.license_system.crypto.LicenseGenerator;
import com.notaris.license_system.crypto.LicenseSystem;
import com.notaris.license_system.crypto.LicenseValidator;
import com.notaris.license_system.model.GeneratedLicense;
import com.notaris.license_system.model.RevokedLicense;
import com.notaris.license_system.repo.GeneratedLicenseRepository;
import com.notaris.license_system.repo.RevokedLicenseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.*;

@Service
public class LicenseService {

    private final Path privPath;
    private final Path pubPath;
    private final Path aesPath;
    private final GeneratedLicenseRepository generatedRepo;
    private final RevokedLicenseRepository revokedRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public LicenseService(@Value("${app.keys.private}") String priv,
            @Value("${app.keys.public}") String pub,
            @Value("${app.keys.aes}") String aes,
            GeneratedLicenseRepository generatedRepo,
            RevokedLicenseRepository revokedRepo) {
        this.privPath = Path.of(priv);
        this.pubPath = Path.of(pub);
        this.aesPath = Path.of(aes);
        this.generatedRepo = generatedRepo;
        this.revokedRepo = revokedRepo;
    }

    public boolean privateExists() {
        return Files.exists(privPath);
    }

    public boolean publicExists() {
        return Files.exists(pubPath);
    }

    public boolean aesExists() {
        return Files.exists(aesPath);
    }

    public String readPrivate() {
        try {
            return Files.readString(privPath);
        } catch (Exception e) {
            return null;
        }
    }

    public String readPublic() {
        try {
            return Files.readString(pubPath);
        } catch (Exception e) {
            return null;
        }
    }

    public String readAesB64() {
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(aesPath));
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public String generateLicense(String customerId, int days, String hw, Map<String, Object> meta, Integer usageLimit,
            String version, boolean useAes) {
        try {
            ECPrivateKey priv = LicenseSystem.loadPrivate(privPath);
            byte[] aes = useAes && aesExists() ? Files.readAllBytes(aesPath) : null;
            LicenseGenerator gen = new LicenseGenerator(priv, aes);
            String lic = gen.generate(customerId, days, hw, meta, usageLimit, version);
            // decode payload for storing
            String payloadB64 = lic.split("\\.")[0];
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            byte[] json;
            if (aes != null && payloadBytes.length > 12) {
                byte[] nonce = Arrays.copyOfRange(payloadBytes, 0, 12);
                byte[] enc = Arrays.copyOfRange(payloadBytes, 12, payloadBytes.length);
                var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(aes, "AES"),
                        new javax.crypto.spec.GCMParameterSpec(128, nonce));
                json = cipher.doFinal(enc);
            } else {
                json = payloadBytes;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            GeneratedLicense gl = generatedRepo.findByUuid((String) data.get("uuid")).orElseGet(GeneratedLicense::new);
            gl.setUuid((String) data.get("uuid"));
            gl.setCustomerId((String) data.get("customer_id"));
            gl.setIssueDate(Instant.parse((String) data.get("issue_date")));
            gl.setExpiryDate(Instant.parse((String) data.get("expiry_date")));
            gl.setHwFingerprint((String) data.get("hw_fingerprint"));
            gl.setMetadataJson(mapper.writeValueAsString(data.get("metadata")));
            if (data.get("usage_limit") != null)
                gl.setUsageLimit((Integer) data.get("usage_limit"));
            gl.setLicenseKey(lic);
            generatedRepo.save(gl);
            return lic;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LicenseValidator.ValidationResult validate(String license, String hw, boolean useAes) {
        try {
            ECPublicKey pub = LicenseSystem.loadPublic(pubPath);
            byte[] aes = (useAes && aesExists()) ? Files.readAllBytes(aesPath) : null;
            LicenseValidator validator = new LicenseValidator(pub, aes, revokedRepo::existsByUuid);
            return validator.validate(license, hw);
        } catch (Exception e) {
            return new LicenseValidator.ValidationResult(false, null, false);
        }
    }

    @Transactional
    public boolean revoke(String uuid) {
        if (revokedRepo.existsByUuid(uuid))
            return false;
        RevokedLicense rl = new RevokedLicense();
        rl.setUuid(uuid);
        revokedRepo.save(rl);
        return true;
    }

    @Transactional
    public void generateKeyPair() {
        try {
            var kp = LicenseSystem.generateKeyPair();
            LicenseSystem.savePrivate((java.security.interfaces.ECPrivateKey) kp.getPrivate(), privPath);
            LicenseSystem.savePublic((java.security.interfaces.ECPublicKey) kp.getPublic(), pubPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteKeyPair() {
        try {
            Files.deleteIfExists(privPath);
            Files.deleteIfExists(pubPath);
        } catch (Exception ignored) {
        }
    }

    public void generateAes() {
        try {
            Files.write(aesPath, LicenseSystem.generateAesKey());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAes() {
        try {
            Files.deleteIfExists(aesPath);
        } catch (Exception ignored) {
        }
    }

    public List<GeneratedLicense> listAll() {
        return generatedRepo.findAll();
    }
}