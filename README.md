# OCT Invoice System — Système de Gestion des Factures Fournisseurs

**Client:** Owendo Container Terminal (OCT)  
**Type:** Enterprise-grade Invoice Management System  
**Status:** Production-ready (Phase 8 complete)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [Documentation](#documentation)
- [Default Credentials](#default-credentials)
- [API Documentation](#api-documentation)
- [Troubleshooting](#troubleshooting)

---

## 🎯 Overview

The **OCT Invoice System** is a modern, full-stack invoice management platform designed for Owendo Container Terminal. It streamlines the entire invoice processing workflow from supplier submission through payment and archival.

### Key Features

- **State Machine Workflow (BAP)**: Full invoice lifecycle with approval stages
- **Department-driven Approvals**: Configurable 1-level or 2-level approval chains
- **Document Management**: Secure file storage with MinIO and SHA-256 integrity verification
- **Real-time Notifications**: Email + WebSocket alerts on workflow events
- **Comprehensive Audit Trails**: Tamper-evident logging of all actions
- **Role-based Access Control**: 6 distinct roles with fine-grained permissions
- **Bilingual UI**: French (primary) + English (secondary)
- **Advanced Reporting**: KPI dashboard, Excel/PDF exports, compliance reports

### Tech Stack

| Component | Technology |
|-----------|-----------|
| **Backend** | Spring Boot 3.4.1 (Java 21) |
| **Database** | PostgreSQL 15+ |
| **Frontend** | React 19 + TypeScript + Vite |
| **File Storage** | MinIO (S3-compatible) |
| **Authentication** | JWT + BCrypt |
| **Orchestration** | Docker Compose |

---

## 📦 Prerequisites

### System Requirements

- **Docker Desktop** 20.10+ (includes Docker Engine & Compose)
- **Java 21 JDK** (for local backend development)
- **Node.js 18+** (for frontend development)
- **Maven 3.8+** (for backend builds)
- **PostgreSQL 15+** (if running outside Docker)
- **Git** (for version control)

### Port Requirements

- **Backend**: `8080` (Spring Boot API)
- **Frontend**: `3000` (Vite dev server)
- **Database**: `5432` (PostgreSQL)
- **MinIO**: `9090` (S3-compatible object storage)
- **MailHog**: `1025/8025` (Email testing)

---

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/mousko667/FINAL-PROJECT-.git
cd invoice-system
```

### 2. Configure Environment

Edit or create `.env` file in the project root:

```env
# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=dany
POSTGRES_DB=oct_invoice_dev
DATABASE_URL=jdbc:postgresql://localhost:5432/oct_invoice_dev

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_REGION=us-east-1

# JWT Security
JWT_SECRET=your-very-long-secure-secret-key-minimum-32-characters
JWT_EXPIRATION=900000

# Email (MailHog)
MAIL_HOST=mailhog
MAIL_PORT=1025
MAIL_FROM=noreply@oct.test

# Application
SPRING_PROFILES_ACTIVE=dev
```

### 3. Start All Services

```bash
# Fresh deployment (clean volumes)
docker-compose down -v
docker-compose up --build

# Subsequent runs
docker-compose up
```

The system is healthy when:
- ✅ PostgreSQL migrations complete (`Flyway` logs show `Successfully applied 12 migrations`)
- ✅ Backend starts: `http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- ✅ Frontend accessible: `http://localhost:3000/login`
- ✅ MinIO console: `http://localhost:9090` (default: minioadmin/minioadmin)
- ✅ MailHog: `http://localhost:8025` (email viewer)

### 4. Login

Navigate to **`http://localhost:3000`** and log in with test credentials.

---

## 🏗️ Architecture

### System Overview

Backend (Spring Boot 3.4.1) → PostgreSQL 15 (12 migrations)  
Frontend (React 19 + TypeScript) → Vite dev server  
File Storage (MinIO) + Email (MailHog) + WebSocket (STOMP)

---

## 📁 Project Structure

```
invoice-system/
├── docs/                          # Project documentation
├── src/main/java/com/oct/invoicesystem/
│   ├── config/                    # Spring configuration
│   ├── domain/                    # Business domains
│   │   ├── auth/
│   │   ├── invoice/
│   │   ├── workflow/
│   │   ├── notification/
│   │   └── report/
│   └── shared/
├── src/test/java/                 # Tests
├── src/resources/
│   ├── db/migration/              # Flyway (V1-V12)
│   └── templates/
├── frontend/                      # React frontend
│   ├── src/
│   ├── e2e/                       # Playwright tests
│   └── package.json
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 🧪 Running Tests

### Backend

```bash
cd invoice-system
mvn clean test
```

### Frontend

```bash
cd invoice-system/frontend
npm test
```

### E2E Tests

```bash
docker-compose up -d
cd invoice-system/frontend
npm run test:e2e
```

---

## 📚 Documentation

- [`docs/PRD.md`](docs/PRD.md) - Product requirements
- [`docs/WORKFLOW.md`](docs/WORKFLOW.md) - BAP invoice workflow
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - Technical architecture
- [`docs/DATABASE.md`](docs/DATABASE.md) - Schema & migrations
- [`docs/API.md`](docs/API.md) - API specifications
- [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) - Code style
- [`docs/TESTING.md`](docs/TESTING.md) - Testing strategy

---

## 🔐 Default Credentials

**Admin:**
```
Username: admin
Password: [see docs/MEMORY.md]
```

**Test Users (password: `password123`):**
- `asst_test` → ROLE_ASSISTANT_COMPTABLE
- `n1_drh_test` → ROLE_VALIDATEUR_N1_DRH
- `daf_test` → ROLE_DAF
- `auditor_test` → ROLE_AUDITEUR

---

## 📖 API Documentation

Access Swagger UI at: **`http://localhost:8080/swagger-ui.html`**

### Key Endpoints

- `POST /api/v1/auth/login` - Authenticate
- `GET /api/v1/invoices` - List invoices
- `POST /api/v1/invoices` - Create invoice
- `POST /api/v1/invoices/{id}/submit` - Submit workflow
- `POST /api/v1/invoices/{id}/workflow/validate-n1` - N1 approval
- `GET /api/v1/reports/kpi` - KPI dashboard

---

## 📄 License

Property of Owendo Container Terminal (OCT).

---

**Version:** 1.0.0 (Production)  
**Last Updated:** 2026-04-11