# M10 #10 — Indicateur de conformité de rétention sur l'écran audit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Afficher un indicateur de conformité de rétention (statut CONFORME / ATTENTION / NON_CONFORME + détails) sur l'écran audit administrateur `/admin/audit`.

**Architecture:** Nouvelle méthode `evaluateCompliance()` dans le `RetentionPolicyService` existant qui calcule un statut à partir de l'entité singleton `RetentionPolicy`, exposée par un nouvel endpoint dédié `GET /api/v1/retention-policy/compliance` (ADMIN). Côté frontend, un composant `RetentionComplianceCard` consomme ce endpoint et s'affiche en haut de l'onglet Journal de `AdminAuditPage`.

**Tech Stack:** Spring Boot 3, JUnit 5 + Mockito + MockMvc, React 18 + TypeScript, @tanstack/react-query, react-i18next, Vitest + Testing Library, lucide-react.

## Global Constraints

- Backend command runner : `./mvnw.cmd` (PAS `./mvnw`).
- SoD (PROB-065) : la rétention est un paramètre technique/conformité système → **ADMIN uniquement**. Jamais d'exposition DAF/financier.
- Toujours `@PreAuthorize` sur chaque méthode de contrôleur ; jamais d'entité JPA exposée (DTO only) ; réponses enveloppées dans `ApiResponse<T>`.
- Pas de magic number : seuil de fraîcheur du balayage via `application.yml` `app.retention.compliance.sweep-max-age-hours:48`.
- i18n frontend : `frontend/src/i18n/{fr,en}.json`, racine `translation` puis chemin `admin.audit.retention.*`.
- Tests frontend : `frontend/src/test/...`.
- `messages_fr.properties` est en ISO-8859-1 (n'est PAS nécessaire ici : endpoint en lecture seule, aucun message backend).
- Critère de fin global : `./mvnw.cmd test` 0 échec + `tsc` 0 + `vitest` vert.

---

## File Structure

- `src/main/java/com/oct/invoicesystem/domain/retention/model/RetentionComplianceStatus.java` — **créer** : enum des 3 statuts.
- `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionComplianceDTO.java` — **créer** : record de sortie.
- `src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyService.java` — **modifier** : ajouter `evaluateCompliance()` + seuil `@Value`.
- `src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyController.java` — **modifier** : ajouter `GET /compliance`.
- `src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyServiceTest.java` — **modifier** : tests unitaires de `evaluateCompliance()`.
- `src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyControllerIntegrationTest.java` — **modifier** : tests d'intégration `GET /compliance`.
- `frontend/src/components/audit/RetentionComplianceCard.tsx` — **créer** : la carte.
- `frontend/src/pages/admin/AdminAuditPage.tsx` — **modifier** : insérer la carte en haut de l'onglet Journal.
- `frontend/src/i18n/fr.json` + `frontend/src/i18n/en.json` — **modifier** : clés `admin.audit.retention.*`.
- `frontend/src/test/components/RetentionComplianceCard.test.tsx` — **créer** : test du composant.
- `docs/COMPLIANCE_MATRIX.md` — **modifier** : ligne 335 🟠 → ✅.

---

## Task 1 : Enum, DTO et calcul de conformité backend

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/model/RetentionComplianceStatus.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionComplianceDTO.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyServiceTest.java`

**Interfaces:**
- Consumes : `RetentionPolicyService.getEntity()` (existant, retourne `RetentionPolicy` avec `isActive()`, `getRetentionYears()`, `getLastSweepAt()`, `getLastFlaggedCount()`, `getUpdatedAt()`).
- Produces : `RetentionComplianceDTO evaluateCompliance()` ; record `RetentionComplianceDTO(RetentionComplianceStatus status, int retentionYears, boolean active, Instant lastSweepAt, Integer lastFlaggedCount, boolean sweepOverdue, Instant updatedAt)` ; enum `RetentionComplianceStatus { CONFORME, ATTENTION, NON_CONFORME }`.

- [ ] **Step 1 : Créer l'enum**

`src/main/java/com/oct/invoicesystem/domain/retention/model/RetentionComplianceStatus.java` :

```java
package com.oct.invoicesystem.domain.retention.model;

/** Global retention-compliance status surfaced on the admin audit screen (M10 #10). */
public enum RetentionComplianceStatus {
    CONFORME,
    ATTENTION,
    NON_CONFORME
}
```

- [ ] **Step 2 : Créer le DTO**

`src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionComplianceDTO.java` :

```java
package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;

import java.time.Instant;

/** Computed retention-compliance view for the admin audit screen (M10 #10). */
public record RetentionComplianceDTO(
        RetentionComplianceStatus status,
        int retentionYears,
        boolean active,
        Instant lastSweepAt,
        Integer lastFlaggedCount,
        boolean sweepOverdue,
        Instant updatedAt
) {}
```

- [ ] **Step 3 : Écrire les tests unitaires qui échouent**

Ajouter dans `RetentionPolicyServiceTest.java`. Ajouter les imports en tête de fichier :

```java
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;
import java.time.temporal.ChronoUnit;
```

Ajouter ces méthodes de test dans la classe :

```java
    private RetentionPolicy policy(boolean active, Instant lastSweepAt, Integer flagged) {
        RetentionPolicy p = RetentionPolicy.builder()
                .retentionYears(10).active(active).build();
        p.setLastSweepAt(lastSweepAt);
        p.setLastFlaggedCount(flagged);
        return p;
    }

    @Test
    void evaluateCompliance_inactivePolicy_isNonConforme() {
        ReflectionTestUtils.setField(service, "sweepMaxAgeHours", 48);
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(policy(false, Instant.now(), 0)));

        RetentionComplianceDTO dto = service.evaluateCompliance();

        assertThat(dto.status()).isEqualTo(RetentionComplianceStatus.NON_CONFORME);
        assertThat(dto.active()).isFalse();
    }

    @Test
    void evaluateCompliance_activeRecentSweepNoFlags_isConforme() {
        ReflectionTestUtils.setField(service, "sweepMaxAgeHours", 48);
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(policy(true, Instant.now().minus(1, ChronoUnit.HOURS), 0)));

        RetentionComplianceDTO dto = service.evaluateCompliance();

        assertThat(dto.status()).isEqualTo(RetentionComplianceStatus.CONFORME);
        assertThat(dto.sweepOverdue()).isFalse();
    }

    @Test
    void evaluateCompliance_activeButFlaggedDocuments_isAttention() {
        ReflectionTestUtils.setField(service, "sweepMaxAgeHours", 48);
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(policy(true, Instant.now().minus(1, ChronoUnit.HOURS), 4)));

        RetentionComplianceDTO dto = service.evaluateCompliance();

        assertThat(dto.status()).isEqualTo(RetentionComplianceStatus.ATTENTION);
        assertThat(dto.lastFlaggedCount()).isEqualTo(4);
    }

    @Test
    void evaluateCompliance_activeButSweepOverdue_isAttention() {
        ReflectionTestUtils.setField(service, "sweepMaxAgeHours", 48);
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(policy(true, Instant.now().minus(72, ChronoUnit.HOURS), 0)));

        RetentionComplianceDTO dto = service.evaluateCompliance();

        assertThat(dto.status()).isEqualTo(RetentionComplianceStatus.ATTENTION);
        assertThat(dto.sweepOverdue()).isTrue();
    }

    @Test
    void evaluateCompliance_activeNeverSwept_isAttentionAndOverdue() {
        ReflectionTestUtils.setField(service, "sweepMaxAgeHours", 48);
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(policy(true, null, null)));

        RetentionComplianceDTO dto = service.evaluateCompliance();

        assertThat(dto.status()).isEqualTo(RetentionComplianceStatus.ATTENTION);
        assertThat(dto.sweepOverdue()).isTrue();
    }
```

- [ ] **Step 4 : Lancer les tests pour vérifier l'échec**

Run : `./mvnw.cmd test -Dtest=RetentionPolicyServiceTest`
Expected : FAIL — compilation error / `evaluateCompliance()` n'existe pas et `sweepMaxAgeHours` introuvable.

- [ ] **Step 5 : Implémenter `evaluateCompliance()` dans le service**

Dans `RetentionPolicyService.java`, ajouter les imports :

```java
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;
import java.time.temporal.ChronoUnit;
```

Ajouter le champ (sous `defaultRetentionYears`) :

```java
    /** Max age (hours) of the last sweep before retention compliance is flagged as overdue. */
    @Value("${app.retention.compliance.sweep-max-age-hours:48}")
    private int sweepMaxAgeHours;
```

Ajouter la méthode (à côté de `get()`) :

```java
    /**
     * Computes the retention compliance status for the admin audit screen (M10 #10).
     * NON_CONFORME when the policy is inactive; ATTENTION when active but the last sweep is
     * overdue or documents remain flagged for disposition; CONFORME otherwise.
     */
    @Transactional(readOnly = true)
    public RetentionComplianceDTO evaluateCompliance() {
        RetentionPolicy p = getEntity();
        Instant lastSweepAt = p.getLastSweepAt();
        boolean sweepOverdue = lastSweepAt == null
                || lastSweepAt.isBefore(Instant.now().minus(sweepMaxAgeHours, ChronoUnit.HOURS));
        int flagged = p.getLastFlaggedCount() == null ? 0 : p.getLastFlaggedCount();

        RetentionComplianceStatus status;
        if (!p.isActive()) {
            status = RetentionComplianceStatus.NON_CONFORME;
        } else if (sweepOverdue || flagged > 0) {
            status = RetentionComplianceStatus.ATTENTION;
        } else {
            status = RetentionComplianceStatus.CONFORME;
        }

        return new RetentionComplianceDTO(status, p.getRetentionYears(), p.isActive(),
                lastSweepAt, p.getLastFlaggedCount(), sweepOverdue, p.getUpdatedAt());
    }
```

- [ ] **Step 6 : Lancer les tests pour vérifier le succès**

Run : `./mvnw.cmd test -Dtest=RetentionPolicyServiceTest`
Expected : PASS (tous les tests, anciens + 5 nouveaux).

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/retention/model/RetentionComplianceStatus.java \
        src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionComplianceDTO.java \
        src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyService.java \
        src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionPolicyServiceTest.java
git commit -m "feat(M10-10): retention compliance evaluation in service + DTO/enum

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2 : Endpoint `GET /retention-policy/compliance`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyControllerIntegrationTest.java`

**Interfaces:**
- Consumes : `RetentionPolicyService.evaluateCompliance()` (Task 1), `ApiResponse.success(T)`.
- Produces : `GET /api/v1/retention-policy/compliance` → `ApiResponse<RetentionComplianceDTO>`, ADMIN only.

- [ ] **Step 1 : Écrire les tests d'intégration qui échouent**

Ajouter dans `RetentionPolicyControllerIntegrationTest.java` :

```java
    @Test
    @WithMockUser(roles = "ADMIN")
    void compliance_asAdmin_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/retention-policy/compliance"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void compliance_asDaf_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/retention-policy/compliance"))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run : `./mvnw.cmd test -Dtest=RetentionPolicyControllerIntegrationTest`
Expected : FAIL — `compliance_asAdmin_returnsOk` retourne 404 (endpoint inexistant).

- [ ] **Step 3 : Ajouter le endpoint**

Dans `RetentionPolicyController.java`, ajouter l'import :

```java
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
```

Ajouter la méthode (après `get()`) :

```java
    @GetMapping("/compliance")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get retention compliance status")
    public ApiResponse<RetentionComplianceDTO> compliance() {
        return ApiResponse.success(service.evaluateCompliance());
    }
```

- [ ] **Step 4 : Lancer pour vérifier le succès**

Run : `./mvnw.cmd test -Dtest=RetentionPolicyControllerIntegrationTest`
Expected : PASS (anciens + 2 nouveaux).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyController.java \
        src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionPolicyControllerIntegrationTest.java
git commit -m "feat(M10-10): GET /retention-policy/compliance endpoint (ADMIN)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3 : i18n frontend

**Files:**
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

**Interfaces:**
- Produces : clés `admin.audit.retention.{title,period,years,active,yes,no,lastSweep,never,flagged,statusConforme,statusAttention,statusNonConforme,sweepOverdue}`.

- [ ] **Step 1 : Ajouter le bloc `retention` dans `fr.json`**

Dans `frontend/src/i18n/fr.json`, à l'intérieur de `admin.audit` (par ex. juste après le bloc `anomalies` qui se termine à `"EXCESSIVE_ACCESS_DENIED": "Accès refusés excessifs"`), ajouter une virgule après l'accolade fermante de `anomalies` puis insérer :

```json
      "retention": {
        "title": "Conformité de la rétention",
        "period": "Période de rétention",
        "years": "{{count}} ans",
        "active": "Politique active",
        "yes": "Oui",
        "no": "Non",
        "lastSweep": "Dernier balayage",
        "never": "Jamais",
        "flagged": "Documents marqués",
        "statusConforme": "Conforme",
        "statusAttention": "Attention",
        "statusNonConforme": "Non conforme",
        "sweepOverdue": "Le dernier balayage de rétention est en retard."
      }
```

- [ ] **Step 2 : Ajouter le bloc `retention` dans `en.json`**

Repérer le bloc `admin.audit` correspondant dans `frontend/src/i18n/en.json` et y ajouter, au même emplacement (après `anomalies`) :

```json
      "retention": {
        "title": "Retention compliance",
        "period": "Retention period",
        "years": "{{count}} years",
        "active": "Policy active",
        "yes": "Yes",
        "no": "No",
        "lastSweep": "Last sweep",
        "never": "Never",
        "flagged": "Flagged documents",
        "statusConforme": "Compliant",
        "statusAttention": "Warning",
        "statusNonConforme": "Non-compliant",
        "sweepOverdue": "The last retention sweep is overdue."
      }
```

- [ ] **Step 3 : Vérifier que le JSON est valide**

Run : `cd frontend && node -e "JSON.parse(require('fs').readFileSync('src/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('src/i18n/en.json','utf8')); console.log('JSON OK')"`
Expected : `JSON OK` (aucune exception de parse).

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(M10-10): i18n keys for retention compliance card

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4 : Composant `RetentionComplianceCard`

**Files:**
- Create: `frontend/src/components/audit/RetentionComplianceCard.tsx`
- Test: `frontend/src/test/components/RetentionComplianceCard.test.tsx`

**Interfaces:**
- Consumes : `GET /retention-policy/compliance` → `{ data: { status, retentionYears, active, lastSweepAt, lastFlaggedCount, sweepOverdue, updatedAt } }` ; clés i18n de Task 3 ; `apiClient` (default export, `apiClient.get`).
- Produces : `export default function RetentionComplianceCard()`.

- [ ] **Step 1 : Écrire le test qui échoue**

`frontend/src/test/components/RetentionComplianceCard.test.tsx` :

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import RetentionComplianceCard from '@/components/audit/RetentionComplianceCard'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <RetentionComplianceCard />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

const base = {
  retentionYears: 10, active: true, lastSweepAt: '2026-06-20T02:30:00Z',
  lastFlaggedCount: 0, sweepOverdue: false, updatedAt: '2026-06-19T10:00:00Z',
}

describe('RetentionComplianceCard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders CONFORME status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'CONFORME' } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Conforme')).toBeInTheDocument())
  })

  it('renders ATTENTION status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'ATTENTION', lastFlaggedCount: 3 } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Attention')).toBeInTheDocument())
  })

  it('renders NON_CONFORME status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'NON_CONFORME', active: false } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Non conforme')).toBeInTheDocument())
  })

  it('renders nothing when the request fails', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('403'))
    const { container } = renderCard()
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled())
    expect(container.textContent).toBe('')
  })
})
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run : `cd frontend && npx vitest run src/test/components/RetentionComplianceCard.test.tsx`
Expected : FAIL — module `RetentionComplianceCard` introuvable.

- [ ] **Step 3 : Implémenter le composant**

`frontend/src/components/audit/RetentionComplianceCard.tsx` :

```tsx
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ShieldCheck } from 'lucide-react'
import apiClient from '@/services/apiClient'
import type { ApiResponse } from '@/types/invoice'

type RetentionStatus = 'CONFORME' | 'ATTENTION' | 'NON_CONFORME'

interface RetentionCompliance {
  status: RetentionStatus
  retentionYears: number
  active: boolean
  lastSweepAt?: string | null
  lastFlaggedCount?: number | null
  sweepOverdue: boolean
  updatedAt?: string | null
}

const STATUS_STYLE: Record<RetentionStatus, { badge: string; key: string }> = {
  CONFORME: { badge: 'bg-green-50 text-green-700 border-green-200', key: 'admin.audit.retention.statusConforme' },
  ATTENTION: { badge: 'bg-amber-50 text-amber-700 border-amber-200', key: 'admin.audit.retention.statusAttention' },
  NON_CONFORME: { badge: 'bg-red-50 text-red-700 border-red-200', key: 'admin.audit.retention.statusNonConforme' },
}

/**
 * M10 #10 — retention compliance indicator on the admin audit screen. Reads the computed
 * compliance status (ADMIN-only endpoint) and renders a status badge plus policy details.
 * Hidden (null) on error / 403, like the anomaly panel.
 */
export default function RetentionComplianceCard() {
  const { t } = useTranslation()
  const { data, isError } = useQuery<RetentionCompliance>({
    queryKey: ['retention-compliance'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<RetentionCompliance>>('/retention-policy/compliance')
      return data.data
    },
    retry: false,
  })

  if (isError || !data) return null

  const style = STATUS_STYLE[data.status]

  return (
    <div className="bg-white rounded-xl border p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-gray-800">{t('admin.audit.retention.title')}</h2>
        </div>
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full border ${style.badge}`}>
          {t(style.key)}
        </span>
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.period')}</dt>
          <dd className="text-gray-700">{t('admin.audit.retention.years', { count: data.retentionYears })}</dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.active')}</dt>
          <dd className="text-gray-700">{data.active ? t('admin.audit.retention.yes') : t('admin.audit.retention.no')}</dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.lastSweep')}</dt>
          <dd className="text-gray-700">
            {data.lastSweepAt ? new Date(data.lastSweepAt).toLocaleString() : t('admin.audit.retention.never')}
          </dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.flagged')}</dt>
          <dd className="text-gray-700">{data.lastFlaggedCount ?? 0}</dd>
        </div>
      </dl>
      {data.sweepOverdue && (
        <p className="mt-3 text-xs text-amber-700">{t('admin.audit.retention.sweepOverdue')}</p>
      )}
    </div>
  )
}
```

- [ ] **Step 4 : Lancer pour vérifier le succès**

Run : `cd frontend && npx vitest run src/test/components/RetentionComplianceCard.test.tsx`
Expected : PASS (4 tests).

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/components/audit/RetentionComplianceCard.tsx \
        frontend/src/test/components/RetentionComplianceCard.test.tsx
git commit -m "feat(M10-10): RetentionComplianceCard component + test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5 : Intégration dans `AdminAuditPage`

**Files:**
- Modify: `frontend/src/pages/admin/AdminAuditPage.tsx`

**Interfaces:**
- Consumes : `RetentionComplianceCard` (Task 4).

- [ ] **Step 1 : Importer le composant**

Dans `frontend/src/pages/admin/AdminAuditPage.tsx`, après `import AuditSummary from '@/components/audit/AuditSummary'` (ligne 8), ajouter :

```tsx
import RetentionComplianceCard from '@/components/audit/RetentionComplianceCard'
```

- [ ] **Step 2 : Insérer la carte en haut de l'onglet Journal**

Toujours dans `AdminAuditPage.tsx`, dans le bloc `{tab === 'journal' && ( <> ... )}`, juste avant `{/* M10: statistical anomaly detection */}` et `<AnomalyPanel />`, insérer :

```tsx
      {/* M10 #10: retention compliance indicator */}
      <RetentionComplianceCard />

```

Le début du bloc journal doit donc se lire :

```tsx
      {tab === 'journal' && (
      <>
      {/* M10 #10: retention compliance indicator */}
      <RetentionComplianceCard />

      {/* M10: statistical anomaly detection */}
      <AnomalyPanel />
```

- [ ] **Step 3 : Vérifier la compilation TypeScript**

Run : `cd frontend && npx tsc --noEmit`
Expected : aucune erreur (sortie vide).

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/pages/admin/AdminAuditPage.tsx
git commit -m "feat(M10-10): show retention compliance card on admin audit journal tab

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6 : Mise à jour de la matrice de conformité + vérification finale

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md`

- [ ] **Step 1 : Passer la ligne 335 de 🟠 à ✅**

Dans `docs/COMPLIANCE_MATRIX.md`, remplacer la ligne :

```
| 10 | Retention period compliance display | 🟠 | Rétention gérée en M9/M14 ; pas d'indicateur de conformité de rétention **sur l'écran audit**. |
```

par :

```
| 10 | Retention period compliance display | ✅ | Carte « Conformité de la rétention » sur /admin/audit (onglet Journal) : statut CONFORME/ATTENTION/NON_CONFORME calculé (GET /retention-policy/compliance, ADMIN, SoD), période, dernier balayage, docs marqués. |
```

- [ ] **Step 2 : Vérification backend complète**

Run : `./mvnw.cmd test`
Expected : BUILD SUCCESS, 0 échec (442 tests : 437 existants + 5 nouveaux service ; les 2 tests d'intégration sont comptés dans le total — confirmer 0 failure / 0 error).

- [ ] **Step 3 : Vérification frontend complète**

Run : `cd frontend && npx tsc --noEmit && npx vitest run`
Expected : tsc sans erreur ; vitest tous verts (51 = 47 existants + 4 nouveaux).

- [ ] **Step 4 : Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(M10-10): mark retention compliance display as done in matrix

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 5 : Pousser si le compte de commits non poussés atteint 10**

Vérifier : `git log --oneline @{u}.. | wc -l` (ou `origin/<branche>..HEAD`). Si ≥ 10, `git push`. Sinon, laisser pour la fin de lot (règle push-every-10-commits ; PR = fin de lot).

---

## Self-Review

**1. Couverture du spec :**
- §3.1 règle de statut → Task 1 (5 cas : inactif, conforme, flagged, overdue, jamais balayé). ✅
- §3.2 enum / §3.3 DTO → Task 1 steps 1-2. ✅
- §3.4 service + seuil `@Value` → Task 1 step 5. ✅
- §3.5 endpoint → Task 2. ✅
- §3.6 tests backend (unit + intégration ADMIN/403) → Task 1 + Task 2. ✅
- §4.1 composant + masquage erreur → Task 4. ✅
- §4.2 emplacement onglet Journal en haut → Task 5. ✅
- §4.3 i18n frontend → Task 3. ✅
- §4.4 test frontend (3 statuts + masquage) → Task 4 step 1. ✅
- §5 COMPLIANCE_MATRIX ligne 335 → Task 6. ✅ (messages_fr backend : non requis, endpoint lecture seule — conforme au spec).
- §6 critère de fin → Task 6 steps 2-3. ✅

**2. Placeholders :** aucun TBD/TODO ; tout code montré en entier.

**3. Cohérence des types :** `evaluateCompliance()` / `RetentionComplianceDTO` / `RetentionComplianceStatus` / champ `sweepMaxAgeHours` / endpoint `/retention-policy/compliance` / clés `admin.audit.retention.*` identiques entre toutes les tâches et le composant. ✅
