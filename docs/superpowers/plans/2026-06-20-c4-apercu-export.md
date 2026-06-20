# C4 — Aperçu in-app avant export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un aperçu in-app des données d'un rapport sauvegardé avant son export/téléchargement (M11 #10, 🟠→✅).

**Architecture:** Extraire de `ReportBuilderService.render()` une méthode `buildDataset(def)` qui produit `(title, columns, rows)` avant sérialisation. `render()` la consomme pour générer le fichier ; un nouveau `preview(id, limit)` la consomme pour renvoyer un JSON tronqué. Un nouvel endpoint `GET /reports/definitions/{id}/preview` expose l'aperçu ; `ReportBuilderPage` ajoute un bouton œil ouvrant une modale.

**Tech Stack:** Spring Boot 3 (Java 17, record DTO, Mockito/JUnit5), React 19 + TypeScript, react-i18next, TanStack Query, Tailwind.

## Global Constraints

- Réponses API toujours enveloppées dans `ApiResponse<T>`.
- `@PreAuthorize` sur chaque méthode de contrôleur ; aperçu réservé à `DAF` + `ASSISTANT_COMPTABLE` (pas d'ADMIN sur données financières).
- Pas d'entité JPA exposée : DTO `record` uniquement.
- i18n frontend = `frontend/src/i18n/{fr,en}.json` en **UTF-8** (pas d'encodage ISO ici).
- `apiClient` baseURL = `/api/v1` → endpoints passés **sans** ce préfixe côté front.
- Pas de migration Flyway (lecture seule, head inchangé).
- `npm run build` = vite seul → lancer `npx tsc --noEmit` ET `npx vitest run` séparément.
- Commit via `git commit -F -` (heredoc bash).
- Signature existante : `TabularExportService.export(Format format, String title, List<String> headers, List<List<String>> rows) : byte[]`.

---

### Task 1: Backend — refactor `buildDataset` + `preview` + DTO

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/report/dto/ReportPreviewDTO.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportBuilderService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportBuilderServiceTest.java`

**Interfaces:**
- Consumes: `ReportDefinitionRepository.findById(UUID)`, `TabularExportService.export(...)`, services existants injectés dans `ReportBuilderService`.
- Produces:
  - `record ReportPreviewDTO(List<String> columns, List<List<String>> rows, int totalRows, String dataset, String format)`
  - `ReportBuilderService.preview(UUID id, int limit) : ReportPreviewDTO`
  - `ReportBuilderService.Dataset buildDataset(ReportDefinition def)` (record interne `Dataset(String title, List<String> columns, List<List<String>> rows)`)

- [ ] **Step 1: Write the failing tests**

Ajouter dans `ReportBuilderServiceTest.java` (imports : `org.mockito.Mockito.verify`, `org.mockito.Mockito.never`, `com.oct.invoicesystem.shared.exception.ResourceNotFoundException`, `java.util.UUID`, `java.time.Instant`, `org.junit.jupiter.api.Assertions.assertNull`) :

```java
    private ReportDefinition invoicesDef() {
        return ReportDefinition.builder().name("Inv").dataset("INVOICES").format("CSV").build();
    }

    @Test
    void preview_truncatesRowsToLimit_butReportsTrueTotal() {
        UUID id = UUID.randomUUID();
        ReportDefinition def = invoicesDef();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(def));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                List.of("FAC-1", "ACME", "100", "XOF", "VALIDE", "2026-01-01", "2026-02-01", "FIN"),
                List.of("FAC-2", "BETA", "200", "XOF", "VALIDE", "2026-01-02", "2026-02-02", "FIN"),
                List.of("FAC-3", "GAMMA", "300", "XOF", "VALIDE", "2026-01-03", "2026-02-03", "FIN")));

        ReportPreviewDTO p = service().preview(id, 2);

        assertEquals(2, p.rows().size());
        assertEquals(3, p.totalRows());
        assertEquals("FAC-1", p.rows().get(0).get(0));
        assertTrue(p.columns().contains("Reference"));
        assertEquals("INVOICES", p.dataset());
    }

    @Test
    void preview_limitAboveTotal_returnsAllRows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(invoicesDef()));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                List.of("FAC-1", "ACME", "100", "XOF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));

        ReportPreviewDTO p = service().preview(id, 50);

        assertEquals(1, p.rows().size());
        assertEquals(1, p.totalRows());
    }

    @Test
    void preview_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service().preview(id, 20));
    }

    @Test
    void preview_doesNotStampLastRunAt() {
        UUID id = UUID.randomUUID();
        ReportDefinition def = invoicesDef();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(def));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any()))
                .thenReturn(List.of(List.of("FAC-1", "ACME", "100", "XOF", "VALIDE", "2026-01-01", "2026-02-01", "FIN")));

        service().preview(id, 20);

        assertNull(def.getLastRunAt());
        verify(repository, never()).save(any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw.cmd -q -Dtest=ReportBuilderServiceTest test`
Expected: FAIL — compilation error (`preview` / `ReportPreviewDTO` introuvables).

- [ ] **Step 3: Create the DTO**

`ReportPreviewDTO.java` :
```java
package com.oct.invoicesystem.domain.report.dto;

import java.util.List;

/** In-app preview of a report definition's dataset (M11 #10), truncated to a row limit. */
public record ReportPreviewDTO(
        List<String> columns,
        List<List<String>> rows,
        int totalRows,
        String dataset,
        String format) {
}
```

- [ ] **Step 4: Refactor `ReportBuilderService` — extract `buildDataset`, add `preview`**

Dans `ReportBuilderService.java` :

1. Ajouter le record interne et la constante de borne, juste après les champs injectés :
```java
    private static final int MAX_PREVIEW_ROWS = 100;

    /** Column headers + data rows for a definition, before serialization to a file format. */
    public record Dataset(String title, List<String> columns, List<List<String>> rows) {}
```

2. Remplacer la méthode `render` par :
```java
    /** Renders the report's dataset into the chosen format. Used by run() and the scheduler. */
    public byte[] render(ReportDefinition def) {
        TabularExportService.Format fmt = TabularExportService.Format.from(def.getFormat());
        Dataset ds = buildDataset(def);
        return exportService.export(fmt, ds.title(), ds.columns(), ds.rows());
    }

    /** Builds the headers + rows for a definition's dataset. Shared by render() and preview(). */
    public Dataset buildDataset(ReportDefinition def) {
        return switch (def.getDataset()) {
            case "INVOICES" -> new Dataset("Invoices",
                    List.of("Reference", "Supplier", "Amount", "Currency", "Status", "Issue date", "Due date", "Department"),
                    invoiceService.buildExportRows(null, null, null, null, null));
            case "SUPPLIERS" -> new Dataset("Suppliers",
                    List.of("Company", "Tax ID", "Email", "Phone", "Status"),
                    supplierService.searchSuppliers(null, null, null, null, Pageable.unpaged()).getContent().stream()
                            .map(s -> List.of(ns(s.companyName()), ns(s.taxId()), ns(s.contactEmail()),
                                    ns(s.contactPhone()), s.status() == null ? "" : s.status().name())).toList());
            case "BUDGET" -> new Dataset("Budget vs Actual",
                    List.of("Department", "Budget", "Actual", "Variance", "Utilization %"),
                    reportService.getBudgetVsActual().lines().stream()
                            .map(l -> List.of(ns(l.departmentCode()),
                                    l.budget() == null ? "" : l.budget().toPlainString(),
                                    l.actual() == null ? "" : l.actual().toPlainString(),
                                    l.variance() == null ? "" : l.variance().toPlainString(),
                                    l.utilizationPercent() == null ? "" : l.utilizationPercent().toPlainString())).toList());
            case "AUDIT" -> new Dataset("Audit",
                    List.of("Date", "Action", "Entity", "Entity ID", "IP"),
                    auditLogRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5000,
                            Sort.by(Sort.Direction.DESC, "createdAt"))).getContent().stream()
                            .map(a -> List.of(a.getCreatedAt() == null ? "" : a.getCreatedAt().toString(),
                                    ns(a.getAction()), ns(a.getEntityType()), ns(a.getEntityId()), ns(a.getIpAddress()))).toList());
            default -> throw new ValidationException("Unknown dataset: " + def.getDataset());
        };
    }

    /** Read-only preview of a definition's dataset, truncated to {@code limit} rows. Does NOT stamp lastRunAt. */
    @Transactional(readOnly = true)
    public com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO preview(UUID id, int limit) {
        ReportDefinition def = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report definition not found: " + id));
        int capped = Math.max(1, Math.min(limit, MAX_PREVIEW_ROWS));
        Dataset ds = buildDataset(def);
        List<List<String>> truncated = ds.rows().size() > capped ? ds.rows().subList(0, capped) : ds.rows();
        return new com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO(
                ds.columns(), List.copyOf(truncated), ds.rows().size(), def.getDataset(), def.getFormat());
    }
```

3. Ajouter l'import en tête de fichier : `import com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO;` puis utiliser `ReportPreviewDTO` non qualifié dans `preview` (remplacer les deux occurrences `com.oct...ReportPreviewDTO` par `ReportPreviewDTO`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw.cmd -q -Dtest=ReportBuilderServiceTest test`
Expected: PASS (anciens tests `render_*`, `create_*` toujours verts + 4 nouveaux).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/dto/ReportPreviewDTO.java \
        src/main/java/com/oct/invoicesystem/domain/report/service/ReportBuilderService.java \
        src/test/java/com/oct/invoicesystem/domain/report/service/ReportBuilderServiceTest.java
git commit -F - <<'EOF'
feat: C4 — backend preview() + buildDataset extraction (M11 #10)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 2: Backend — endpoint `GET /definitions/{id}/preview`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: `ReportBuilderService.preview(UUID, int)`, `ReportPreviewDTO`.
- Produces: `GET /api/v1/reports/definitions/{id}/preview?limit=20` → `ApiResponse<ReportPreviewDTO>`.

- [ ] **Step 1: Write the failing controller test**

Ouvrir `ReportControllerTest.java`, repérer un test existant sur `/definitions` pour copier le style de mock (`reportBuilderService`) et l'auth (`@WithMockUser(roles=...)` ou MockMvc + jwt). Ajouter :

```java
    @Test
    @WithMockUser(roles = "DAF")
    void previewDefinition_returnsPreview_forDaf() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportBuilderService.preview(eq(id), anyInt())).thenReturn(
                new ReportPreviewDTO(List.of("Reference"), List.of(List.of("FAC-1")), 1, "INVOICES", "CSV"));

        mockMvc.perform(get("/api/v1/reports/definitions/{id}/preview", id).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.columns[0]").value("Reference"))
                .andExpect(jsonPath("$.data.rows[0][0]").value("FAC-1"));
    }

    @Test
    @WithMockUser(roles = "ROLE_SUPPLIER")
    void previewDefinition_forbidden_forUnauthorizedRole() throws Exception {
        mockMvc.perform(get("/api/v1/reports/definitions/{id}/preview", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
```

Ajuster les imports/annotations d'auth pour matcher le reste du fichier (si le fichier utilise `.with(jwt()...)` plutôt que `@WithMockUser`, suivre ce pattern ; pour le cas interdit utiliser un rôle non listé, p.ex. `ASSISTANT` invalide ou `SUPPLIER`). Importer `ReportPreviewDTO`, `org.mockito.ArgumentMatchers.eq/anyInt`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd -q -Dtest=ReportControllerTest test`
Expected: FAIL — endpoint inexistant (404) / `preview` non mocké.

- [ ] **Step 3: Add the endpoint**

Dans `ReportController.java`, après `runDefinition(...)` :
```java
    @GetMapping("/definitions/{id}/preview")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Preview a report definition's dataset before export",
            description = "Returns the column headers and the first N rows (M11 #10) without generating a file")
    public ApiResponse<com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO> previewDefinition(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(reportBuilderService.preview(id, limit));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw.cmd -q -Dtest=ReportControllerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java \
        src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java
git commit -F - <<'EOF'
feat: C4 — GET /reports/definitions/{id}/preview endpoint (M11 #10)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 3: Frontend — bouton œil + modale d'aperçu + i18n

**Files:**
- Modify: `frontend/src/pages/ReportBuilderPage.tsx`
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

**Interfaces:**
- Consumes: `GET /reports/definitions/{id}/preview?limit=20` → `{ data: ReportPreview }`.
- Produces: aucun (feuille de l'arbre).

- [ ] **Step 1: Add i18n keys**

Dans `frontend/src/i18n/fr.json`, sous l'objet `reportBuilder` (et `app` pour `close`) :
```json
"preview": "Aperçu",
"previewTitle": "Aperçu du rapport",
"previewShowing": "Aperçu des {{shown}} premières lignes sur {{total}}",
"previewEmpty": "Aucune donnée à afficher.",
"download": "Télécharger"
```
Dans `app` (si `close` absent) : `"close": "Fermer"`.

Dans `frontend/src/i18n/en.json`, mêmes clés :
```json
"preview": "Preview",
"previewTitle": "Report preview",
"previewShowing": "Showing first {{shown}} of {{total}} rows",
"previewEmpty": "No data to display.",
"download": "Download"
```
`app.close`: `"Close"`.

- [ ] **Step 2: Add preview state + handler + Eye import**

Dans `ReportBuilderPage.tsx` :
1. Import lucide : ajouter `Eye, X` à la liste existante (`FileBarChart, Loader2, Plus, Trash2, Play, FileText`).
2. Ajouter le type sous `interface ReportDef {...}` :
```ts
interface ReportPreview { columns: string[]; rows: string[][]; totalRows: number; dataset: string; format: string }
```
3. Dans le composant, après l'état `running` :
```ts
  const [preview, setPreview] = useState<{ def: ReportDef; data: ReportPreview } | null>(null)
  const [previewing, setPreviewing] = useState<string | null>(null)

  const openPreview = async (def: ReportDef) => {
    setPreviewing(def.id)
    try {
      const res = await apiClient.get<{ data: ReportPreview }>(`/reports/definitions/${def.id}/preview`, { params: { limit: 20 } })
      setPreview({ def, data: res.data.data })
    } finally { setPreviewing(null) }
  }
```

- [ ] **Step 3: Add the Eye button in the Actions cell**

Dans la cellule Actions (le `<div className="flex items-center gap-3 justify-end">`), **avant** le bouton Play :
```tsx
                        <button onClick={() => openPreview(d)} disabled={previewing === d.id} className="text-gray-500 hover:text-primary" title={t('reportBuilder.preview', 'Aperçu')}>
                          {previewing === d.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
                        </button>
```

- [ ] **Step 4: Add the preview modal**

Juste avant la fermeture `</div>` racine du `return` (après le dernier bloc `</div>` du tableau, à l'intérieur du conteneur `space-y-6`), insérer :
```tsx
        {preview && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
               onClick={() => setPreview(null)}>
            <div className="bg-white rounded-xl shadow-xl max-w-3xl w-full max-h-[80vh] flex flex-col"
                 onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between px-5 py-4 border-b">
                <div>
                  <h2 className="font-semibold text-gray-900">{preview.def.name}</h2>
                  <span className="text-xs text-gray-500">{preview.data.dataset} · {preview.data.format}</span>
                </div>
                <button onClick={() => setPreview(null)} className="text-gray-400 hover:text-gray-600" title={t('app.close', 'Fermer')}>
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="overflow-auto p-5 flex-1">
                {preview.data.rows.length === 0 ? (
                  <p className="text-sm text-gray-400 text-center py-8">{t('reportBuilder.previewEmpty', 'Aucune donnée à afficher.')}</p>
                ) : (
                  <table className="w-full text-sm border-collapse">
                    <thead><tr className="border-b text-left text-gray-500">
                      {preview.data.columns.map((c, i) => <th key={i} className="px-3 py-2 font-medium whitespace-nowrap">{c}</th>)}
                    </tr></thead>
                    <tbody>
                      {preview.data.rows.map((row, ri) => (
                        <tr key={ri} className="border-b last:border-0">
                          {row.map((cell, ci) => <td key={ci} className="px-3 py-2 text-gray-700 whitespace-nowrap">{cell}</td>)}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              <div className="flex items-center justify-between px-5 py-4 border-t">
                <span className="text-xs text-gray-500">
                  {t('reportBuilder.previewShowing', { shown: preview.data.rows.length, total: preview.data.totalRows })}
                </span>
                <div className="flex items-center gap-2">
                  <button onClick={() => setPreview(null)} className="px-3 py-2 text-sm border rounded-lg hover:bg-gray-50">{t('app.close', 'Fermer')}</button>
                  <button onClick={() => runReport(preview.def)} disabled={running === preview.def.id}
                    className="inline-flex items-center gap-2 px-3 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
                    {running === preview.def.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                    {t('reportBuilder.download', 'Télécharger')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
```
Ajouter `Download` à l'import lucide (en plus de `Eye, X`).

- [ ] **Step 5: Verify type-check and build**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend" && npx tsc --noEmit && npx vitest run && npm run build`
Expected: tsc vert ; vitest = seuls les **4 échecs pré-existants** connus (InvoiceTimeline, useAuth, 3 e2e Playwright) ; build vite vert.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/ReportBuilderPage.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -F - <<'EOF'
feat: C4 — aperçu in-app (bouton œil + modale) dans le constructeur de rapports (M11 #10)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 4: Discipline — bascule matrice de conformité

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md`

- [ ] **Step 1: Flip M11 #10 to ✅**

Ligne 371 : passer `🟠` → `✅` et remplacer le commentaire « **pas d'aperçu in-app** avant export » par « Aperçu in-app (bouton œil → modale colonnes + N lignes) via `GET /reports/definitions/{id}/preview` ✅ ». Ligne 390 « Gaps M11 » : retirer « ; #10 pas d'aperçu avant export ».

- [ ] **Step 2: Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -F - <<'EOF'
docs: C4 — M11 #10 🟠→✅ aperçu in-app avant export

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Self-Review

- **Spec coverage :** buildDataset/preview (Task 1), DTO (Task 1), endpoint + rôles (Task 2), bouton œil + modale + i18n + bouton télécharger (Task 3), pas de Flyway (respecté), bascule matrice (Task 4). Tous les points de la spec sont couverts.
- **Placeholder scan :** code complet à chaque étape, aucune mention TBD/TODO.
- **Type consistency :** `ReportPreview`/`ReportPreviewDTO(columns, rows, totalRows, dataset, format)` identique front/back ; `preview(UUID, int)`, `buildDataset(def)`, `Dataset(title, columns, rows)` cohérents entre tâches ; `runReport(def)`/`running` réutilisés tels qu'existants.
- **Note d'exécution :** les tests de contrôleur (Task 2 Step 1) doivent suivre le pattern d'auth réel du fichier `ReportControllerTest` — vérifier `@WithMockUser` vs `jwt()` avant d'écrire.
