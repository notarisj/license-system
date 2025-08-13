# License System

Minimal license management service (web UI + JSON API) featuring:
- ECDSA P‑521 signing (BouncyCastle)
- Optional AES‑GCM encryption of license payloads
- Hardware fingerprint binding (optional)
- Metadata & usage limit fields
- License revocation tracking
- Filtering & pagination UI
- Simple API key based authorization for create/revoke endpoints
- In‑memory admin authentication (form login)
- SQLite persistence (licenses + revocations)

## Quick Start

Build & run (Java 21):

```bash
./mvnw spring-boot:run
```

Package WAR:

```bash
./mvnw clean package
```

Result: `target/license-system-0.0.1-SNAPSHOT.war`

Main entrypoint: [`com.notaris.license_system.LicenseSystemApplication`](src/main/java/com/notaris/license_system/LicenseSystemApplication.java)

## Configuration

Edit [src/main/resources/application.properties](src/main/resources/application.properties):

```
spring.datasource.url=jdbc:sqlite:licenses.db
app.keys.private=private.pem
app.keys.public=public.pem
app.keys.aes=aes.key
app.api.whitelist=token1,token2,token3
app.admin.username=admin
app.admin.password=changeMe123
```

Keys are stored as PEM (ECDSA) / raw (AES). Rotate the sample keys before production use.

Generated metadata reference: [src/main/resources/META-INF/spring-configuration-metadata.json](src/main/resources/META-INF/spring-configuration-metadata.json)

## Security

Form login (username/password from properties) via Spring Security config: [`com.notaris.license_system.config.SecurityConfig`](src/main/java/com/notaris/license_system/config/SecurityConfig.java)  
API key whitelist: [`com.notaris.license_system.config.ApiKeyConfig`](src/main/java/com/notaris/license_system/config/ApiKeyConfig.java)

All non-/login routes require authentication (UI). API create/revoke additionally require an API key (header `X-API-KEY` or query `api_key`).

## Data Model

Entities:
- [`com.notaris.license_system.model.GeneratedLicense`](src/main/java/com/notaris/license_system/model/GeneratedLicense.java)
- [`com.notaris.license_system.model.RevokedLicense`](src/main/java/com/notaris/license_system/model/RevokedLicense.java)

Repositories:
- [`com.notaris.license_system.repo.GeneratedLicenseRepository`](src/main/java/com/notaris/license_system/repo/GeneratedLicenseRepository.java)
- [`com.notaris.license_system.repo.RevokedLicenseRepository`](src/main/java/com/notaris/license_system/repo/RevokedLicenseRepository.java)

Service layer: [`com.notaris.license_system.service.LicenseService`](src/main/java/com/notaris/license_system/service/LicenseService.java)

Crypto helpers:
- Generator: [`com.notaris.license_system.crypto.LicenseGenerator`](src/main/java/com/notaris/license_system/crypto/LicenseGenerator.java)
- Validator: [`com.notaris.license_system.crypto.LicenseValidator`](src/main/java/com/notaris/license_system/crypto/LicenseValidator.java)
- Key ops: [`com.notaris.license_system.crypto.LicenseSystem`](src/main/java/com/notaris/license_system/crypto/LicenseSystem.java)

## Web UI

Controller: [`com.notaris.license_system.controller.WebController`](src/main/java/com/notaris/license_system/controller/WebController.java)  
Templates in [src/main/resources/templates](src/main/resources/templates).

Features: create, validate, key/AES management, revoke, list licenses.

## REST API

Controller: [`com.notaris.license_system.controller.ApiController`](src/main/java/com/notaris/license_system/controller/ApiController.java)

### 1. Validate (no API key required)

```
POST /api/validate
{
  "license_key": "...",
  "hw_fingerprint": "...",   // optional
  "use_aes": true            // optional
}
```

Response:
```
{
  "valid": true|false,
  "revoked": true|false,
  "license_data": { ... } | null
}
```

### 2. Create (API key required)

```
POST /api/create
Headers: X-API-KEY: token1
{
  "customer_id": "cust123",
  "days_valid": 30,
  "hw_fingerprint": "...",    // optional
  "metadata": { "plan": "pro"},
  "usage_limit": 100,          // optional
  "version": "2.0",
  "use_aes": true
}
```

Response:
```
{ "license_key": "..." }
```

### 3. Revoke (API key required)

```
POST /api/revoke
Headers: X-API-KEY: token1
{ "uuid": "license-uuid" }
```

Response:
```
{ "revoked": true, "already_revoked": false }
```

## Hardware Fingerprint

Utility method: [`com.notaris.license_system.crypto.LicenseValidator#hardwareFingerprint`](src/main/java/com/notaris/license_system/crypto/LicenseValidator.java)

Client should compute and send this when binding / validating.

## Key & AES Management

UI at /keys uses:
- `Generate Key Pair` → creates P‑521 ECDSA keys (PEM)
- `Generate AES Key` → random 256‑bit key stored at `aes.key`

License creation / validation toggles AES via checkbox / `use_aes` field.

## Build Notes

SQLite DB file: `licenses.db` (created automatically).  
DDL managed by Hibernate (update mode).  
Java 21 required.

## Example cURL

Create (API key):
```bash
curl -X POST http://localhost:8080/api/create \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: token1' \
  -d '{"customer_id":"c1","days_valid":14,"version":"2.0"}'
```

Validate:
```bash
curl -X POST http://localhost:8080/api/validate \
  -H 'Content-Type: application/json' \
  -d '{"license_key":"<paste>"}'
```

Revoke:
```bash
curl -X POST http://localhost:8080/api/revoke \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: token1' \
  -d '{"uuid":"<uuid-from-license>"}'
```

## Security Considerations

Replace bundled sample keys before production.  
Protect the SQLite file and key files with proper filesystem permissions.  
Consider moving API key storage to a secure external store for rotation.

## License

See [LICENSE](LICENSE) for license details.