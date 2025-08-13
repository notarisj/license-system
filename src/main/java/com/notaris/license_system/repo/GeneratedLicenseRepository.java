package com.notaris.license_system.repo;

import com.notaris.license_system.model.GeneratedLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneratedLicenseRepository extends JpaRepository<GeneratedLicense, Long> {
    Optional<GeneratedLicense> findByUuid(String uuid);
}
