package com.notaris.license_system.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class GeneratedLicense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64, nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private Instant issueDate;

    @Column(nullable = false)
    private Instant expiryDate;

    private String hwFingerprint;

    @Lob
    private String metadataJson;

    private Integer usageLimit;

    @Lob
    @Column(nullable = false)
    private String licenseKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}