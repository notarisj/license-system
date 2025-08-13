package com.notaris.license_system.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
public class RevokedLicense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64, nullable = false)
    private String uuid;

    @Column(nullable = false, updatable = false)
    private Instant revokedAt = Instant.now();
}