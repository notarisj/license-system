package com.notaris.license_system.controller;

import com.notaris.license_system.config.ApiKeyConfig;
import com.notaris.license_system.crypto.LicenseValidator;
import com.notaris.license_system.service.LicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final LicenseService service;
    private final ApiKeyConfig apiKeyConfig;

    public ApiController(LicenseService service, ApiKeyConfig apiKeyConfig) {
        this.service = service;
        this.apiKeyConfig = apiKeyConfig;
    }

    private boolean auth(String key) {
        return apiKeyConfig.isAuthorized(key);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, Object> body) {
        try {
            String lic = (String) body.get("license_key");
            if (lic == null)
                return ResponseEntity.badRequest().body(Map.of("error", "license_key is required"));
            String hw = (String) body.get("hw_fingerprint");
            boolean useAes = Boolean.TRUE.equals(body.get("use_aes"));
            LicenseValidator.ValidationResult res = service.validate(lic, hw, useAes);
            return ResponseEntity.ok(Map.of(
                    "valid", res.valid(),
                    "revoked", res.revoked(),
                    "license_data", res.data()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestHeader(value = "X-API-KEY", required = false) String key,
            @RequestParam(value = "api_key", required = false) String keyParam,
            @RequestBody Map<String, Object> body) {
        if (!auth(key != null ? key : keyParam))
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        try {
            String customer = (String) body.get("customer_id");
            if (customer == null)
                return ResponseEntity.badRequest().body(Map.of("error", "customer_id is required"));
            int days = body.get("days_valid") == null ? 30 : ((Number) body.get("days_valid")).intValue();
            String hw = (String) body.get("hw_fingerprint");
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) body.get("metadata");
            Integer usage = body.get("usage_limit") == null ? null : ((Number) body.get("usage_limit")).intValue();
            String version = (String) body.getOrDefault("version", "2.0");
            boolean useAes = Boolean.TRUE.equals(body.get("use_aes"));
            String lic = service.generateLicense(customer, days, hw, meta, usage, version, useAes);
            return ResponseEntity.ok(Map.of("license_key", lic));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@RequestHeader(value = "X-API-KEY", required = false) String key,
            @RequestParam(value = "api_key", required = false) String keyParam,
            @RequestBody Map<String, Object> body) {
        if (!auth(key != null ? key : keyParam))
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        String uuid = (String) body.get("uuid");
        if (uuid == null)
            return ResponseEntity.badRequest().body(Map.of("error", "uuid is required"));
        boolean created = service.revoke(uuid);
        return ResponseEntity.ok(Map.of("revoked", true, "already_revoked", !created));
    }
}