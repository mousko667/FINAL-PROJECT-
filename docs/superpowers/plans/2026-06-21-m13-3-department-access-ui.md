# M13 #3 — UI accès par département — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un écran d'administration en lecture seule qui expose, par département, les utilisateurs rattachés, leurs rôles et le niveau de validation (N1 / N1→N2), comblant le gap d'audit M13 #3.

**Architecture:** Nouvel endpoint d'agrégation read-only `GET /api/v1/admin/department-access` (ADMIN only) → `DepartmentAccessService` qui agrège `DepartmentRepository` + `UserRepository` → DTO sans aucune donnée financière. Frontend : page `/admin/department-access` (accordéons par dépt) + entrée Sidebar + route, gardées ADMIN.

**Tech Stack:** Spring Boot 3.x, JPA, MapStruct (non requis ici), JUnit5 + Mockito + MockMvc ; React 18 + TypeScript, vitest, react-i18next.

## Global Constraints

- `./mvnw.cmd` au backend racine ; front depuis `frontend/`.
- AUCUNE migration Flyway (pas de nouvelle table) → V64 reste non consommée.
- apiClient SANS préfixe `/api/v1` (PROB-038).
- DTO et réponse : AUCUN champ financier (pas de `Department.budget`, pas de `User.approvalLimit`, aucun montant). SoD PROB-065.
- Endpoint : `@PreAuthorize("hasRole('ADMIN')")`. Front : `RoleGuard fallback={null}` en nav, `PageRoleGuard` en page (PROB-004).
- Erreurs front via `t(key)` (PROB-006). i18n FR + EN à parité.
- Lombok : entité `Department` a getter `isActive()` ; `User` a `isActive()` (champ `active`).
- Réponses API enveloppées dans `ApiResponse<T>`.
- Règle no-failures : backend 0 échec, `tsc` 0, vitest vert.

---

## File Structure

**Backend (créés) :**
- `src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentAccessDTO.java` — record aperçu par dépt.
- `src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentUserDTO.java` — record user+rôles.
- `src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessService.java` — interface.
- `src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceImpl.java` — agrégation.
- `src/main/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessController.java` — endpoint.

**Backend (modifiés) :**
- `src/main/java/com/oct/invoicesystem/domain/user/repository/UserRepository.java` — ajout `findByDepartmentIdIn`.

**Backend (tests) :**
- `src/test/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceTest.java`
- `src/test/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessControllerTest.java`

**Frontend (créés) :**
- `frontend/src/services/departmentAccessService.ts`
- `frontend/src/pages/admin/DepartmentAccessPage.tsx`
- `frontend/src/pages/admin/__tests__/DepartmentAccessPage.test.tsx`

**Frontend (modifiés) :**
- `frontend/src/AppRoutes.tsx` — route `/admin/department-access`.
- `frontend/src/components/layout/Sidebar.tsx` — entrée nav admin.
- `frontend/src/i18n/fr.json` + `frontend/src/i18n/en.json` — clés.

---

## Task 1: DTOs backend (records, sans données financières)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentUserDTO.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentAccessDTO.java`

**Interfaces:**
- Produces:
  - `DepartmentUserDTO(UUID userId, String fullName, String username, boolean active, List<String> roles)`
  - `DepartmentAccessDTO(UUID departmentId, String code, String nameFr, String nameEn, boolean requiresN2, String n1Role, String n2Role, int userCount, int activeCount, List<DepartmentUserDTO> users)`

- [ ] **Step 1: Créer `DepartmentUserDTO.java`**

```java
package com.oct.invoicesystem.domain.department.dto;

import java.util.List;
import java.util.UUID;

/** Vue lecture seule d'un utilisateur dans l'aperçu des accès par département.
 *  Ne contient aucune donnée financière (pas d'approvalLimit). */
public record DepartmentUserDTO(
        UUID userId,
        String fullName,
        String username,
        boolean active,
        List<String> roles
) {}
```

- [ ] **Step 2: Créer `DepartmentAccessDTO.java`**

```java
package com.oct.invoicesystem.domain.department.dto;

import java.util.List;
import java.util.UUID;

/** Aperçu lecture seule des accès d'un département : utilisateurs rattachés,
 *  rôles et niveau de validation. Aucune donnée financière (pas de budget). */
public record DepartmentAccessDTO(
        UUID departmentId,
        String code,
        String nameFr,
        String nameEn,
        boolean requiresN2,
        String n1Role,
        String n2Role,
        int userCount,
        int activeCount,
        List<DepartmentUserDTO> users
) {}
```

- [ ] **Step 3: Compiler**

Run: `./mvnw.cmd -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentUserDTO.java src/main/java/com/oct/invoicesystem/domain/department/dto/DepartmentAccessDTO.java
git commit -m "feat(m13-3): DTOs aperçu acces par departement (sans donnee financiere)"
```

---

## Task 2: Requête repository `findByDepartmentIdIn`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/user/repository/UserRepository.java`

**Interfaces:**
- Produces: `List<User> findByDepartmentIdIn(Collection<UUID> departmentIds)` (users avec userRoles+role chargés via EntityGraph).

- [ ] **Step 1: Ajouter l'import et la méthode**

Dans `UserRepository.java`, ajouter l'import :

```java
import java.util.Collection;
import java.util.List;
```

Et la méthode (avant la fermeture de l'interface) :

```java
    // M13 #3: charge en une passe les users d'un ensemble de départements + leurs rôles (évite N+1).
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    List<User> findByDepartmentIdIn(Collection<UUID> departmentIds);
```

(Note : `java.util.UUID` est déjà importé ; `List` peut déjà être référencé en FQN ailleurs — n'ajouter l'import `java.util.List` que s'il n'existe pas, sinon utiliser le FQN comme le reste du fichier.)

- [ ] **Step 2: Compiler**

Run: `./mvnw.cmd -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/user/repository/UserRepository.java
git commit -m "feat(m13-3): UserRepository.findByDepartmentIdIn (EntityGraph roles)"
```

---

## Task 3: Service d'agrégation (TDD)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessService.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceImpl.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceTest.java`

**Interfaces:**
- Consumes: `DepartmentRepository.findAll()`, `UserRepository.findByDepartmentIdIn(...)`, `DepartmentAccessDTO`, `DepartmentUserDTO`.
- Produces: `List<DepartmentAccessDTO> DepartmentAccessService.getDepartmentAccessOverview()`.

- [ ] **Step 1: Écrire le test qui échoue**

```java
package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentAccessServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DepartmentAccessServiceImpl service;

    private Department dept(UUID id, String code, boolean requiresN2) {
        return Department.builder().id(id).code(code).nameFr(code + " FR").nameEn(code + " EN")
                .requiresN2(requiresN2).n1Role("ROLE_N1").n2Role(requiresN2 ? "ROLE_N2" : null).build();
    }

    private User user(UUID deptId, String username, boolean active, String roleName) {
        Role role = Role.builder().name(roleName).build();
        User u = User.builder().id(UUID.randomUUID()).username(username).firstName("F").lastName("L")
                .departmentId(deptId).active(active).build();
        u.setUserRoles(Set.of(UserRole.builder().user(u).role(role).build()));
        return u;
    }

    @Test
    void overview_groupsUsersByDepartment_andCountsActive() {
        UUID d1 = UUID.randomUUID();
        Department dep = dept(d1, "IT", true);
        when(departmentRepository.findAll()).thenReturn(List.of(dep));
        when(userRepository.findByDepartmentIdIn(anyCollection()))
                .thenReturn(List.of(user(d1, "alice", true, "ROLE_N1"), user(d1, "bob", false, "ROLE_N2")));

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result).hasSize(1);
        DepartmentAccessDTO it = result.get(0);
        assertThat(it.code()).isEqualTo("IT");
        assertThat(it.requiresN2()).isTrue();
        assertThat(it.userCount()).isEqualTo(2);
        assertThat(it.activeCount()).isEqualTo(1);
        assertThat(it.users()).extracting("username").containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void overview_departmentWithoutUsers_hasZeroCounts() {
        UUID d1 = UUID.randomUUID();
        when(departmentRepository.findAll()).thenReturn(List.of(dept(d1, "RH", false)));
        when(userRepository.findByDepartmentIdIn(anyCollection())).thenReturn(List.of());

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userCount()).isZero();
        assertThat(result.get(0).activeCount()).isZero();
        assertThat(result.get(0).users()).isEmpty();
    }

    @Test
    void overview_mapsRoleNames() {
        UUID d1 = UUID.randomUUID();
        when(departmentRepository.findAll()).thenReturn(List.of(dept(d1, "IT", false)));
        when(userRepository.findByDepartmentIdIn(anyCollection()))
                .thenReturn(List.of(user(d1, "alice", true, "ROLE_VALIDATEUR_N1_IT")));

        List<DepartmentAccessDTO> result = service.getDepartmentAccessOverview();

        assertThat(result.get(0).users().get(0).roles()).containsExactly("ROLE_VALIDATEUR_N1_IT");
    }
}
```

- [ ] **Step 2: Créer l'interface**

```java
package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;

import java.util.List;

public interface DepartmentAccessService {

    /** Retourne, par département (trié par code), les utilisateurs rattachés, leurs rôles
     *  et le niveau de validation. Lecture seule ; aucune donnée financière. */
    List<DepartmentAccessDTO> getDepartmentAccessOverview();
}
```

- [ ] **Step 3: Lancer le test → échec attendu (impl manquante)**

Run: `./mvnw.cmd -q -o test -Dtest=DepartmentAccessServiceTest`
Expected: FAIL — `DepartmentAccessServiceImpl` n'existe pas / non instanciable.

- [ ] **Step 4: Créer l'implémentation**

```java
package com.oct.invoicesystem.domain.department.service;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUserDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentAccessServiceImpl implements DepartmentAccessService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentAccessDTO> getDepartmentAccessOverview() {
        List<Department> departments = departmentRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .toList();

        List<UUID> ids = departments.stream().map(Department::getId).toList();
        Map<UUID, List<User>> byDept = ids.isEmpty()
                ? Map.of()
                : userRepository.findByDepartmentIdIn(ids).stream()
                        .collect(Collectors.groupingBy(User::getDepartmentId));

        return departments.stream()
                .map(d -> toDto(d, byDept.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    private DepartmentAccessDTO toDto(Department d, List<User> users) {
        List<DepartmentUserDTO> userDtos = users.stream()
                .map(this::toUserDto)
                .sorted((a, b) -> a.username().compareToIgnoreCase(b.username()))
                .toList();
        int activeCount = (int) users.stream().filter(User::isActive).count();
        return new DepartmentAccessDTO(
                d.getId(), d.getCode(), d.getNameFr(), d.getNameEn(),
                d.isRequiresN2(), d.getN1Role(), d.getN2Role(),
                users.size(), activeCount, userDtos);
    }

    private DepartmentUserDTO toUserDto(User u) {
        List<String> roles = u.getUserRoles() == null ? List.of()
                : u.getUserRoles().stream().map(ur -> ur.getRole().getName()).sorted().toList();
        return new DepartmentUserDTO(
                u.getId(), u.getFirstName() + " " + u.getLastName(), u.getUsername(), u.isActive(), roles);
    }
}
```

- [ ] **Step 5: Lancer le test → succès attendu**

Run: `./mvnw.cmd -q -o test -Dtest=DepartmentAccessServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessService.java src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceImpl.java src/test/java/com/oct/invoicesystem/domain/department/service/DepartmentAccessServiceTest.java
git commit -m "feat(m13-3): DepartmentAccessService agregation users/roles par dept (TDD)"
```

---

## Task 4: Controller + test SoD (TDD)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessControllerTest.java`

**Interfaces:**
- Consumes: `DepartmentAccessService.getDepartmentAccessOverview()`, `ApiResponse`.
- Produces: `GET /api/v1/admin/department-access` → `ApiResponse<List<DepartmentAccessDTO>>`, ADMIN only.

- [ ] **Step 1: Écrire le test qui échoue (200 admin, 403 non-admin, 401 anonyme)**

```java
package com.oct.invoicesystem.domain.department.controller;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.service.DepartmentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepartmentAccessControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DepartmentAccessService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void overview_AsAdmin_Returns200() throws Exception {
        when(service.getDepartmentAccessOverview()).thenReturn(List.of(
                new DepartmentAccessDTO(UUID.randomUUID(), "IT", "Info", "IT", true, "ROLE_N1", "ROLE_N2", 0, 0, List.of())));

        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("IT"));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void overview_AsDaf_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void overview_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/department-access"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Lancer le test → échec attendu (endpoint absent → 404/401)**

Run: `./mvnw.cmd -q -o test -Dtest=DepartmentAccessControllerTest`
Expected: FAIL (le mapping n'existe pas encore).

- [ ] **Step 3: Créer le controller**

```java
package com.oct.invoicesystem.domain.department.controller;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.service.DepartmentAccessService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/department-access")
@RequiredArgsConstructor
@Tag(name = "Department Access", description = "Aperçu lecture seule des accès par département (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentAccessController {

    private final DepartmentAccessService departmentAccessService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aperçu des accès par département",
            description = "Liste, par département, les utilisateurs rattachés, leurs rôles et le niveau de validation (Admin uniquement). Aucune donnée financière.")
    public ResponseEntity<ApiResponse<List<DepartmentAccessDTO>>> getDepartmentAccessOverview() {
        return ResponseEntity.ok(ApiResponse.success(departmentAccessService.getDepartmentAccessOverview()));
    }
}
```

- [ ] **Step 4: Lancer le test → succès attendu**

Run: `./mvnw.cmd -q -o test -Dtest=DepartmentAccessControllerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessController.java src/test/java/com/oct/invoicesystem/domain/department/controller/DepartmentAccessControllerTest.java
git commit -m "feat(m13-3): endpoint GET /admin/department-access (ADMIN only, 403 SoD)"
```

---

## Task 5: Service frontend + types TS

**Files:**
- Create: `frontend/src/services/departmentAccessService.ts`

**Interfaces:**
- Produces:
  - `interface DepartmentAccessUser { userId; fullName; username; active; roles: string[] }`
  - `interface DepartmentAccess { departmentId; code; nameFr; nameEn; requiresN2; n1Role; n2Role; userCount; activeCount; users: DepartmentAccessUser[] }`
  - `departmentAccessService.getOverview(): Promise<DepartmentAccess[]>`

- [ ] **Step 1: Créer le service**

```ts
import apiClient from '@/services/apiClient'
import type { ApiResponse } from '@/types/invoice'

export interface DepartmentAccessUser {
  userId: string
  fullName: string
  username: string
  active: boolean
  roles: string[]
}

export interface DepartmentAccess {
  departmentId: string
  code: string
  nameFr: string
  nameEn: string
  requiresN2: boolean
  n1Role: string | null
  n2Role: string | null
  userCount: number
  activeCount: number
  users: DepartmentAccessUser[]
}

export const departmentAccessService = {
  // apiClient n'ajoute PAS le préfixe /api/v1 (PROB-038).
  getOverview: async (): Promise<DepartmentAccess[]> => {
    const { data } = await apiClient.get<ApiResponse<DepartmentAccess[]>>('/admin/department-access')
    return data.data
  },
}
```

(Si `ApiResponse` n'est pas exporté depuis `@/types/invoice`, le confirmer en ouvrant le fichier ; sinon typer le retour Axios sur `{ data: { data: DepartmentAccess[] } }`.)

- [ ] **Step 2: Vérifier la compilation TS**

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/departmentAccessService.ts
git commit -m "feat(m13-3): service front departmentAccess (apiClient sans prefixe)"
```

---

## Task 6: Page React + test (vitest)

**Files:**
- Create: `frontend/src/pages/admin/DepartmentAccessPage.tsx`
- Test: `frontend/src/pages/admin/__tests__/DepartmentAccessPage.test.tsx`

**Interfaces:**
- Consumes: `departmentAccessService.getOverview()`, clés i18n de la Task 8.
- Produces: `export default function DepartmentAccessPage()`.

- [ ] **Step 1: Écrire le test qui échoue**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import DepartmentAccessPage from '../DepartmentAccessPage'
import { departmentAccessService } from '@/services/departmentAccessService'

vi.mock('@/services/departmentAccessService')
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'fr' } }),
}))

describe('DepartmentAccessPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('affiche les départements avec compteurs et badge N1->N2', async () => {
    vi.mocked(departmentAccessService.getOverview).mockResolvedValue([
      { departmentId: '1', code: 'IT', nameFr: 'Informatique', nameEn: 'IT', requiresN2: true,
        n1Role: 'ROLE_N1', n2Role: 'ROLE_N2', userCount: 2, activeCount: 1,
        users: [{ userId: 'u1', fullName: 'Alice L', username: 'alice', active: true, roles: ['ROLE_N1'] }] },
    ])

    render(<DepartmentAccessPage />)

    await waitFor(() => expect(screen.getByText('IT')).toBeInTheDocument())
    expect(screen.getByText('Informatique')).toBeInTheDocument()
  })

  it('affiche un état vide', async () => {
    vi.mocked(departmentAccessService.getOverview).mockResolvedValue([])
    render(<DepartmentAccessPage />)
    await waitFor(() =>
      expect(screen.getByText('departmentAccess.empty')).toBeInTheDocument())
  })

  it('affiche une erreur traduite', async () => {
    vi.mocked(departmentAccessService.getOverview).mockRejectedValue(new Error('boom'))
    render(<DepartmentAccessPage />)
    await waitFor(() =>
      expect(screen.getByText('departmentAccess.error')).toBeInTheDocument())
  })
})
```

- [ ] **Step 2: Lancer → échec attendu (page absente)**

Run: `cd frontend && npx vitest run src/pages/admin/__tests__/DepartmentAccessPage.test.tsx`
Expected: FAIL (module introuvable).

- [ ] **Step 3: Créer la page**

```tsx
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { departmentAccessService, type DepartmentAccess } from '@/services/departmentAccessService'

export default function DepartmentAccessPage() {
  const { t, i18n } = useTranslation()
  const [data, setData] = useState<DepartmentAccess[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    departmentAccessService
      .getOverview()
      .then(setData)
      .catch(() => setError('departmentAccess.error'))
      .finally(() => setLoading(false))
  }, [])

  const name = (d: DepartmentAccess) => (i18n.language === 'en' ? d.nameEn : d.nameFr)

  if (loading) return <div className="p-6">{t('common.loading')}</div>
  if (error) return <div className="p-6 text-red-600">{t(error)}</div>

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{t('departmentAccess.title')}</h1>
        <p className="text-sm text-gray-500">{t('departmentAccess.subtitle')}</p>
      </div>

      {data.length === 0 ? (
        <p className="text-gray-500">{t('departmentAccess.empty')}</p>
      ) : (
        <div className="space-y-3">
          {data.map((d) => (
            <details key={d.departmentId} className="rounded-lg border border-gray-200 bg-white">
              <summary className="cursor-pointer px-4 py-3 flex items-center justify-between">
                <span className="font-medium text-gray-900">
                  {d.code} · {name(d)}
                </span>
                <span className="flex items-center gap-3 text-sm text-gray-600">
                  <span className="rounded bg-gray-100 px-2 py-0.5">
                    {d.requiresN2 ? t('departmentAccess.levelN1N2') : t('departmentAccess.levelN1')}
                  </span>
                  <span>{t('departmentAccess.counts', { total: d.userCount, active: d.activeCount })}</span>
                </span>
              </summary>

              <div className="px-4 pb-4">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b">
                      <th className="py-2">{t('departmentAccess.colName')}</th>
                      <th className="py-2">{t('departmentAccess.colUsername')}</th>
                      <th className="py-2">{t('departmentAccess.colStatus')}</th>
                      <th className="py-2">{t('departmentAccess.colRoles')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {d.users.map((u) => (
                      <tr key={u.userId} className="border-b last:border-0">
                        <td className="py-2 text-gray-900">{u.fullName}</td>
                        <td className="py-2 text-gray-600">{u.username}</td>
                        <td className="py-2">
                          <span className={u.active ? 'text-green-700' : 'text-gray-400'}>
                            {u.active ? t('departmentAccess.active') : t('departmentAccess.inactive')}
                          </span>
                        </td>
                        <td className="py-2">
                          <span className="flex flex-wrap gap-1">
                            {u.roles.map((r) => (
                              <span key={r} className="rounded bg-blue-50 text-blue-700 px-2 py-0.5 text-xs">
                                {r}
                              </span>
                            ))}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Lancer → succès attendu**

Run: `cd frontend && npx vitest run src/pages/admin/__tests__/DepartmentAccessPage.test.tsx`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/admin/DepartmentAccessPage.tsx frontend/src/pages/admin/__tests__/DepartmentAccessPage.test.tsx
git commit -m "feat(m13-3): page DepartmentAccessPage (accordeons par dept, lecture seule)"
```

---

## Task 7: Route + entrée Sidebar (gardées ADMIN)

**Files:**
- Modify: `frontend/src/AppRoutes.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

**Interfaces:**
- Consumes: `DepartmentAccessPage`, patterns `PageRoleGuard`/`RoleGuard` existants.

- [ ] **Step 1: Repérer les patterns existants**

Ouvrir `AppRoutes.tsx` et chercher une route admin existante (ex. `/admin/permissions` ou `/admin/users`) pour copier exactement la forme du guard (`PageRoleGuard` + import lazy/direct). Ouvrir `Sidebar.tsx` et repérer le groupe admin + un item gardé par `RoleGuard` (`roles={['ROLE_ADMIN']}` ou équivalent, `fallback={null}`).

- [ ] **Step 2: Ajouter la route**

Dans `AppRoutes.tsx`, importer la page (même style d'import que les autres pages admin) :

```tsx
import DepartmentAccessPage from '@/pages/admin/DepartmentAccessPage'
```

Ajouter la route à côté des autres routes admin, en copiant la forme exacte du guard utilisé par `/admin/permissions` (remplacer le `path` et l'`element`) :

```tsx
<Route path="/admin/department-access" element={
  <PageRoleGuard roles={['ROLE_ADMIN']}>
    <DepartmentAccessPage />
  </PageRoleGuard>
} />
```

(Adapter `PageRoleGuard`/prop `roles` au nom et à la signature réels constatés à l'étape 1.)

- [ ] **Step 3: Ajouter l'entrée Sidebar**

Dans `Sidebar.tsx`, dans le groupe admin, ajouter un item en copiant exactement un item admin existant (même `RoleGuard`, `fallback={null}`) :

```tsx
<RoleGuard roles={['ROLE_ADMIN']} fallback={null}>
  <NavLink to="/admin/department-access">{t('nav.departmentAccess')}</NavLink>
</RoleGuard>
```

(Adapter au composant de lien et au wrapper réels du fichier.)

- [ ] **Step 4: Compilation TS**

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/AppRoutes.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(m13-3): route + entree Sidebar /admin/department-access (ADMIN)"
```

---

## Task 8: i18n FR + EN (parité)

**Files:**
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

- [ ] **Step 1: Ajouter le bloc FR**

Dans `fr.json`, ajouter la clé `nav.departmentAccess` dans le groupe `nav` existant, et un bloc `departmentAccess` :

```json
"departmentAccess": {
  "title": "Accès par département",
  "subtitle": "Vue en lecture seule des utilisateurs, rôles et niveau de validation par département.",
  "empty": "Aucun département.",
  "error": "Impossible de charger les accès par département.",
  "levelN1": "Validation N1",
  "levelN1N2": "Validation N1 → N2",
  "counts": "{{total}} utilisateurs · {{active}} actifs",
  "colName": "Nom",
  "colUsername": "Identifiant",
  "colStatus": "Statut",
  "colRoles": "Rôles",
  "active": "Actif",
  "inactive": "Inactif"
}
```

Et `"departmentAccess": "Accès par département"` sous `nav`.

- [ ] **Step 2: Ajouter le bloc EN (mêmes clés)**

Dans `en.json` :

```json
"departmentAccess": {
  "title": "Department Access",
  "subtitle": "Read-only view of users, roles and validation level per department.",
  "empty": "No department.",
  "error": "Unable to load department access.",
  "levelN1": "N1 validation",
  "levelN1N2": "N1 → N2 validation",
  "counts": "{{total}} users · {{active}} active",
  "colName": "Name",
  "colUsername": "Username",
  "colStatus": "Status",
  "colRoles": "Roles",
  "active": "Active",
  "inactive": "Inactive"
}
```

Et `"departmentAccess": "Department Access"` sous `nav`.

- [ ] **Step 3: Vérifier la parité + JSON valide**

Run: `cd frontend && npx tsc --noEmit && npx vitest run src/pages/admin/__tests__/DepartmentAccessPage.test.tsx`
Expected: 0 erreur TS, tests verts (les clés résolvent).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(m13-3): i18n FR+EN departmentAccess + nav"
```

---

## Task 9: Gate complet + mise à jour COMPLIANCE_MATRIX

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (M13 UI #3 et Feature #3 : 🟠 → ✅ ; ajuster « Gaps M13 »).

- [ ] **Step 1: Gate backend complet**

Run: `./mvnw.cmd -q -o test`
Expected: 0 échec, 0 erreur.

- [ ] **Step 2: Gate frontend complet**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: tsc 0 erreur ; vitest tout vert.

- [ ] **Step 3: Mettre à jour COMPLIANCE_MATRIX.md**

- Ligne M13 UI #3 : `🟠` → `✅`, preuve : « `/admin/department-access` : aperçu lecture seule users×rôles×niveau N1/N2 par département (ADMIN). »
- Ligne M13 Feature #3 : `🟠` → `✅`, preuve : « UI dédiée d'accès par département (M13 #3). »
- Bloc « Gaps M13 » : retirer la mention du gap #3 (ou le marquer résolu 2026-06-21).
- Recompter la colonne récap M13 si elle compte les 🟠.

- [ ] **Step 4: Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(m13-3): COMPLIANCE_MATRIX M13 #3 + feature #3 -> resolu (UI dediee)"
```

---

## Self-Review

- **Couverture spec :** §4 backend → Tasks 1-4 ; §5 frontend → Tasks 5-7 ; §6 i18n → Task 8 ; §7 tests → tests dans chaque task + gate Task 9 ; §3 SoD → `@PreAuthorize` (Task 4) + test 403 + DTO sans champ financier (Task 1) ; §2 « pas de migration » → respecté (aucune migration dans le plan). Critères d'acceptation §8 → couverts par Task 9 (gate + matrice).
- **Placeholders :** aucun TBD/TODO ; code complet à chaque étape.
- **Cohérence des types :** `DepartmentAccessDTO`/`DepartmentUserDTO` identiques entre Tasks 1, 3, 4 ; `getDepartmentAccessOverview()` cohérent Tasks 3-4 ; `getOverview()` (front) cohérent Tasks 5-6 ; clés `departmentAccess.*` cohérentes Tasks 6 et 8.
- **Réserves d'intégration** (signalées dans les steps, à confirmer à l'exécution sur le code réel) : nom exact du guard de route (`PageRoleGuard`) et signature de `RoleGuard`/prop `roles` dans `Sidebar.tsx`/`AppRoutes.tsx` ; export de `ApiResponse` depuis `@/types/invoice`. Ces points sont gérés par « repérer le pattern existant » en Task 7 / note Task 5.
