package com.notaris.license_system.repo;

import com.notaris.license_system.model.RevokedLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RevokedLicenseRepository extends JpaRepository<RevokedLicense, Long> {
    boolean existsByUuid(String uuid);

    Optional<RevokedLicense> findByUuid(String uuid);
}