package com.notaris.license_system.controller;

import com.notaris.license_system.model.GeneratedLicense;
import com.notaris.license_system.repo.RevokedLicenseRepository;
import com.notaris.license_system.service.LicenseService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Validated
public class WebController {

    private final LicenseService service;
    private final RevokedLicenseRepository revokedRepo;

    public WebController(LicenseService service, RevokedLicenseRepository revokedRepo) {
        this.service = service;
        this.revokedRepo = revokedRepo;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/create")
    public String createForm() {
        return "create";
    }

    @PostMapping("/create")
    public String createSubmit(Model model,
            @RequestParam @NotBlank String customerId,
            @RequestParam(defaultValue = "30") int daysValid,
            @RequestParam(required = false) String hwFingerprint,
            @RequestParam(required = false) String metadata,
            @RequestParam(required = false) Integer usageLimit,
            @RequestParam(defaultValue = "2.0") String version,
            @RequestParam(value = "aes", required = false) boolean aes) {
        Map<String, Object> metaMap = null;
        try {
            if (metadata != null && !metadata.isBlank()) {
                metaMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        metadata,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {
        }
        String license = service.generateLicense(customerId, daysValid, hwFingerprint, metaMap, usageLimit, version,
                aes);
        model.addAttribute("licenseKey", license);
        return "create";
    }

    @GetMapping("/validate")
    public String validateForm() {
        return "validate";
    }

    @PostMapping("/validate")
    public String validateSubmit(Model model,
            @RequestParam("licenseKey") String licenseKey,
            @RequestParam(required = false) String hwFingerprint,
            @RequestParam(value = "aes", required = false) boolean aes) {
        var res = service.validate(licenseKey, hwFingerprint, aes);
        model.addAttribute("result", res.valid() ? "VALID" : "INVALID");
        model.addAttribute("revoked", res.revoked());
        model.addAttribute("licenseData", res.data());
        if (res.data() != null) {
            try {
                model.addAttribute("prettyData", new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(res.data()));
            } catch (Exception ignored) {
            }
        }
        // preserve submitted values
        model.addAttribute("licenseKeyInput", licenseKey);
        model.addAttribute("hwFingerprintInput", hwFingerprint);
        model.addAttribute("aesChecked", aes);
        return "validate";
    }

    @GetMapping("/keys")
    public String keys(Model model) {
        populateKeys(model, null);
        return "keys_and_aes";
    }

    @PostMapping(value = "/keys", params = "generate_keypair")
    public String genKey(Model model) {
        service.generateKeyPair();
        populateKeys(model, "Key pair generated.");
        return "keys_and_aes";
    }

    @PostMapping(value = "/keys", params = "delete_key")
    public String delKey(Model model) {
        service.deleteKeyPair();
        populateKeys(model, "Key pair deleted.");
        return "keys_and_aes";
    }

    @PostMapping(value = "/keys", params = "generate_aes")
    public String genAes(Model model) {
        service.generateAes();
        populateKeys(model, "AES key generated.");
        return "keys_and_aes";
    }

    @PostMapping(value = "/keys", params = "delete_aes")
    public String delAes(Model model) {
        service.deleteAes();
        populateKeys(model, "AES key deleted.");
        return "keys_and_aes";
    }

    private void populateKeys(Model model, String msg) {
        model.addAttribute("privExists", service.privateExists());
        model.addAttribute("pubExists", service.publicExists());
        model.addAttribute("aesExists", service.aesExists());
        model.addAttribute("privKey", service.readPrivate());
        model.addAttribute("pubKey", service.readPublic());
        model.addAttribute("aesKey", service.readAesB64());
        model.addAttribute("message", msg);
    }

    @GetMapping("/revoke")
    public String revokeForm() {
        return "revoke";
    }

    @PostMapping("/revoke")
    public String revokeSubmit(Model model, @RequestParam String uuid) {
        boolean created = service.revoke(uuid.trim());
        model.addAttribute("message",
                created ? "License UUID " + uuid + " revoked." : "License UUID " + uuid + " already revoked.");
        return "revoke";
    }

    @GetMapping("/licenses")
    public String listLicenses(Model model,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customer", required = false) String customer,
            @RequestParam(value = "page", defaultValue = "0") int page) {
        List<GeneratedLicense> all = service.listAll();
        // search
        if (q != null && !q.isBlank()) {
            String ql = q.toLowerCase();
            all = all.stream()
                    .filter(gl -> (gl.getCustomerId() != null && gl.getCustomerId().toLowerCase().contains(ql)) ||
                            (gl.getUuid() != null && gl.getUuid().toLowerCase().contains(ql)) ||
                            (gl.getHwFingerprint() != null && gl.getHwFingerprint().toLowerCase().contains(ql)))
                    .collect(Collectors.toList());
        }
        if (customer != null && !customer.isBlank()) {
            String cl = customer.toLowerCase();
            all = all.stream().filter(gl -> gl.getCustomerId() != null && gl.getCustomerId().toLowerCase().contains(cl))
                    .collect(Collectors.toList());
        }
        Instant now = Instant.now();
        record Row(GeneratedLicense gl, String status) {
        }
        List<Row> rows = new ArrayList<>();
        for (GeneratedLicense gl : all) {
            boolean revoked = revokedRepo.existsByUuid(gl.getUuid());
            boolean expired = now.isAfter(gl.getExpiryDate());
            String st = revoked ? "Revoked" : expired ? "Expired" : "Valid";
            rows.add(new Row(gl, st));
        }
        if (status != null && !status.isBlank()) {
            String sl = status.toLowerCase();
            rows = rows.stream().filter(r -> r.status().toLowerCase().equals(sl)).collect(Collectors.toList());
        }
        int pageSize = 5;
        int from = page * pageSize;
        int to = Math.min(from + pageSize, rows.size());
        List<Row> slice = from >= rows.size() ? List.of() : rows.subList(from, to);
        model.addAttribute("rows", slice);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", (rows.size() + pageSize - 1) / pageSize);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("statusFilter", status == null ? "" : status);
        model.addAttribute("customerFilter", customer == null ? "" : customer);
        return "licenses_list";
    }
}