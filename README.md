# OCT Invoice System

Digital and secure supplier invoice validation management system for Owendo Container Terminal (OCT).

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Spring Boot 3.4.1, Java 21, Maven |
| Database | PostgreSQL 18, Flyway baseline V1-V34 |
| Frontend | React 19, TypeScript, Vite |
| Storage | MinIO S3-compatible object storage |
| Security | RS256 JWT, BCrypt, MFA/TOTP, AES-256-GCM for sensitive fields, TLS 1.3 in prod |
| Tooling | Docker Compose, JUnit 5, Testcontainers, Vitest, Playwright |

## Prerequisites

- Java 21 JDK
- Node.js 20+
- Docker Desktop
- PostgreSQL 18 running outside Docker on host port `5433`
- Database `oct_invoice` owned by/user `postgres`

`docker-compose.yml` does not start PostgreSQL. The backend container reaches the host database through `host.docker.internal:5433`.

## Environment

Create a local `.env` file in the project root. This file is ignored by Git.

```env
APP_PORT=8080
FRONTEND_PORT=3000
SPRING_PROFILES_ACTIVE=dev

DB_HOST=host.docker.internal
DB_PORT=5433
DB_NAME=oct_invoice
DB_USER=postgres
DB_PASSWORD=<local-db-password>

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<local-minio-access-key>
MINIO_SECRET_KEY=<local-minio-secret-key>
MINIO_BUCKET=oct-invoices
MINIO_CONSOLE_PORT=9001

JWT_PRIVATE_KEY=<base64-pkcs8-rsa-private-key>
JWT_PUBLIC_KEY=<base64-x509-rsa-public-key>
JWT_EXPIRATION_MS=86400000
JWT_REFRESH_EXPIRATION_MS=604800000

ENCRYPTION_KEY=<32-character-local-dev-key>

# TLS 1.3 (prod profile only — generate keystore locally, see below)
SSL_KEYSTORE_PATH=certs/keystore.p12
SSL_KEYSTORE_PASSWORD=<local-keystore-password>

MAIL_HOST=mailhog
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=noreply@oct.local
MAIL_FROM_NAME=OCT Invoice System

VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=http://localhost:8080/ws
```

Generate RS256 keys for development with:

```bash
openssl genrsa -out private.pem 2048
openssl pkcs8 -topk8 -inform PEM -outform DER -in private.pem -nocrypt | base64 -w0
openssl rsa -in private.pem -pubout -outform DER | base64 -w0
```

## Secret Management

| Environment | Where secrets live | Committed files |
| --- | --- | --- |
| **Production** | Environment variables / secrets manager only | Placeholders in `.env.example` and `application.yaml` (`prod` profile) |
| **Local dev** | Gitignored `.env` (copy from `.env.example`) | Never commit `.env`, `*.p12`, or `certs/` |
| **Automated tests** | `src/test/resources/application-test.yml` | TEST-ONLY RSA/AES/MinIO keys clearly marked — not production secrets |

Rules:

- `ProdSecretConfigValidator` fails fast if any prod secret is missing.
- Rotating `ENCRYPTION_KEY` invalidates existing encrypted bank details — only rotate on a fresh database.
- CI loads test credentials from `application-test.yml`; GitHub Actions no longer needs committed JWT material.

## Run With Docker Compose

Start PostgreSQL 18 first, then run:

```bash
docker compose up --build
```

Services:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- MailHog: `http://localhost:8025`

Flyway applies the consolidated baseline migrations `V1` through `V34` on a fresh database.

## TLS 1.3 Proof

Production TLS is configured in `src/main/resources/application.yaml` under the `prod` profile:

- `server.ssl.enabled=true`
- `server.ssl.protocol=TLSv1.3`
- `server.ssl.enabled-protocols=TLSv1.3`
- `server.ssl.key-store=${SSL_KEYSTORE_PATH}`
- `server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}`
- `server.ssl.key-store-type=PKCS12`

For local thesis evidence, generate a self-signed PKCS12 keystore. The generated file and password are local proof material only and must not be committed.

```powershell
New-Item -ItemType Directory -Force certs
keytool -genkeypair -alias oct -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore certs\keystore.p12 -storepass change-me-local-only -keypass change-me-local-only -dname "CN=localhost, OU=OCT, O=Owendo Container Terminal, L=Libreville, C=GA"
```

Add these local-only values to `.env` when running the prod profile:

```env
SSL_KEYSTORE_PATH=certs/keystore.p12
SSL_KEYSTORE_PASSWORD=change-me-local-only
```

Run the backend with TLS:

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:SSL_KEYSTORE_PATH='certs\keystore.p12'
$env:SSL_KEYSTORE_PASSWORD='change-me-local-only'
.\mvnw.cmd spring-boot:run
```

Capture the handshake proof:

```powershell
curl.exe -vk --tlsv1.3 https://localhost:8080/actuator/health
```

Expected proof markers include a TLSv1.3 connection and a self-signed certificate for `CN=localhost`.

Captured thesis evidence: `docs/audit/tls-handshake-proof.txt` and `docs/audit/tls-keystore-info.txt`.

## Tests

Backend:

```powershell
.\mvnw.cmd test
```

Frontend:

```powershell
cd frontend
npm test -- --run
```

Frontend build:

```powershell
cd frontend
npm run build
```

## Default Demonstration Accounts

All seeded demonstration users below use password `Test1234!` and are for dev/test only.

| Username | Role |
| --- | --- |
| `aa` | `ROLE_ASSISTANT_COMPTABLE` |
| `daf` | `ROLE_DAF` |
| `drh` | `ROLE_VALIDATEUR_N1_DRH` |
| `dg` | `ROLE_VALIDATEUR_N1_DG` |
| `rsi` | `ROLE_VALIDATEUR_N1_INFO` |
| `dsi` | `ROLE_VALIDATEUR_N2_INFO` |
| `dex` | `ROLE_VALIDATEUR_N1_TERM` |
| `com` | `ROLE_VALIDATEUR_N1_COM` |
| `qhsse` | `ROLE_VALIDATEUR_N1_QHSSE` |
| `infra` | `ROLE_VALIDATEUR_N1_INFRA` |
| `dir_infra` | `ROLE_VALIDATEUR_N2_INFRA` |
| `atelier` | `ROLE_VALIDATEUR_N1_TECH` |
| `dir_tech` | `ROLE_VALIDATEUR_N2_TECH` |
| `supplier` | `ROLE_SUPPLIER` |

There is no `ROLE_AUDITEUR`; audit access is split by separation of duties between `ROLE_ADMIN` for system/security audit and `ROLE_DAF` for financial audit.

## Documentation

- `docs/PRD.md`
- `docs/WORKFLOW.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE.md`
- `docs/API.md`
- `docs/CONVENTIONS.md`
- `docs/TESTING.md`
- `docs/TASKS.md`
