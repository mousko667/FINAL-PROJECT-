# C4 — Aperçu in-app avant export (M11 #10)

**Date :** 2026-06-20
**Lot :** Section C, familles B+C — item C4
**Référence exigence :** M11 #10 « Report preview and export (PDF, Excel) » — statut 🟠 dans `docs/COMPLIANCE_MATRIX.md`

## Problème

Tout l'export du constructeur de rapports fonctionne (CSV/Excel/PDF via `/reports/definitions/{id}/run`).
Le gap unique de la matrice de conformité : **aucun aperçu in-app des données avant le téléchargement**.
L'utilisateur doit télécharger le fichier pour voir ce qu'il contient.

## Périmètre

- **Cible :** le constructeur de rapports (`ReportBuilderPage.tsx`), chaque `ReportDefinition` sauvegardée.
- **Hors scope :** les exports de `ReportsPage` (« Data Exports »), dont les données sont déjà visibles à l'écran.
- **Hors scope :** modification de la logique métier de construction des datasets (réutilisée telle quelle).

## Architecture & flux de données

```
ReportBuilderPage (bouton Eye par ligne)
        │ GET /reports/definitions/{id}/preview?limit=20
        ▼
ReportController.previewDefinition(id, limit)
        │
        ▼
ReportBuilderService.preview(id, limit) ──► buildDataset(def)  ◄── render(def) appelle aussi buildDataset
        │                                    (colonnes + lignes, source unique)
        ▼
ReportPreviewDTO { columns, rows (tronquées), totalRows, dataset, format }
```

**Cœur du design :** extraire de `render()` une méthode `Dataset buildDataset(ReportDefinition def)` qui
retourne `(title, columns, rows)` *avant* sérialisation. `render()` la consomme puis appelle
`exportService.export(...)` ; `preview()` la consomme, tronque à `limit`, et renvoie `totalRows` = taille
avant troncature.

**Garantie :** l'aperçu et le fichier téléchargé proviennent de la même construction de données → aucune
divergence possible. Vrai car `buildDataset` ne dépend pas du `Format` : seul `exportService.export(fmt, …)`
connaît le format, les lignes sont identiques quel que soit CSV/Excel/PDF.

## Backend

### Nouveau DTO — `domain/report/dto/ReportPreviewDTO.java`
```java
record ReportPreviewDTO(List<String> columns, List<List<String>> rows, int totalRows,
                        String dataset, String format)
```

### Refactor `ReportBuilderService`
Type interne :
```java
record Dataset(String title, List<String> columns, List<List<String>> rows) {}
```
Méthodes :
- `Dataset buildDataset(ReportDefinition def)` — contient le `switch(dataset)` actuel, **sans**
  `exportService.export`. Logique métier inchangée.
- `byte[] render(ReportDefinition def)` — devient : `Dataset ds = buildDataset(def);
  return exportService.export(fmt, ds.title(), ds.columns(), ds.rows());`
- `ReportPreviewDTO preview(UUID id, int limit)` — `findById` → `buildDataset` → tronque à `limit` lignes,
  `totalRows = rows.size()` (avant troncature). **N'horodate PAS `lastRunAt`** (un aperçu n'est pas une
  exécution ; seul `run()` horodate).

### Nouvel endpoint — `ReportController`
```java
@GetMapping("/definitions/{id}/preview")
@PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
public ApiResponse<ReportPreviewDTO> previewDefinition(@PathVariable UUID id,
        @RequestParam(defaultValue = "20") int limit)
```
- Mêmes rôles que les autres endpoints `/definitions` (séparation des pouvoirs : pas d'accès ADMIN aux
  données financières).
- `limit` borné à un maximum (100) pour éviter un aperçu massif.

## Frontend — `ReportBuilderPage.tsx`

### État & handler
```ts
interface ReportPreview { columns: string[]; rows: string[][]; totalRows: number; dataset: string; format: string }
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

### Bouton Eye
Ajouté dans la colonne Actions, **avant** le bouton Play, avec spinner pendant le chargement.

### Modale d'aperçu (`{preview && …}`)
- En-tête : nom du rapport + badge dataset/format.
- Corps : `<table>` colonnes = `preview.data.columns`, lignes = `preview.data.rows` ; état vide si
  `rows.length === 0`.
- Pied : « Aperçu des N premières lignes sur {totalRows} » (i18n interpolé) + bouton **Télécharger**
  (réutilise `runReport(preview.def)`) + bouton Fermer.
- Fermeture : clic overlay + touche Échap + bouton ✕.

### i18n — `frontend/src/i18n/{fr,en}.json` (UTF-8)
Clés : `reportBuilder.preview`, `reportBuilder.previewTitle`, `reportBuilder.previewShowing`
(`{{shown}}`/`{{total}}`), `reportBuilder.previewEmpty`, `reportBuilder.download`, `app.close` (si absente).

## Tests

### Backend (TDD RED→GREEN)
`ReportBuilderServiceTest` :
- `preview` retourne colonnes + lignes tronquées à `limit` ; `totalRows` = total réel non tronqué.
- `preview` avec `limit ≥ total` → toutes les lignes, `totalRows == rows.size()`.
- `preview` sur id inconnu → `ResourceNotFoundException`.
- `preview` **n'horodate pas** `lastRunAt`.
- Non-régression : `render`/`run` inchangés (cas existants restent verts sans modification).

`ReportControllerTest` :
- `GET /definitions/{id}/preview` : 200 + forme JSON pour DAF / ASSISTANT_COMPTABLE.
- 403 pour un rôle non autorisé.

### Frontend
- Test vitest du flux modale **uniquement si** un harnais de test composant existe déjà pour
  ReportBuilderPage ; sinon ne pas en créer un hors scope.

## Vérification finale (cadence du lot)
- `mvnw.cmd test` (ciblé report) → vert.
- `npx tsc --noEmit` → vert.
- `npx vitest run` → seuls les 4 échecs pré-existants connus subsistent.
- `npm run build` (vite) → vert.
- **Pas de migration Flyway** (lecture seule, aucun schéma touché → head inchangé).

## Discipline
- Bascule M11 #10 🟠→✅ dans `docs/COMPLIANCE_MATRIX.md`.
- Tout bug réel → `docs/KNOWN_ISSUES_REGISTRY.md` avant commit.
- Commit unique : `feat: C4 — aperçu in-app avant export (M11 #10)`.
- **Push seulement à la fin du lot, après validation utilisateur.**
