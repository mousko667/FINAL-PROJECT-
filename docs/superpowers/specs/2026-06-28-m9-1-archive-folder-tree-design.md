# M9 #1 — Arborescence de dossiers dans /archive

**Date :** 2026-06-28
**Branche :** chore/sanitize-docs-migrations
**Module :** M9 #1 (Digital Archiving — folder structure)
**TASKS.md :** ligne 373

---

## Contexte vérifié dans le code

`/archive` est aujourd'hui un index plat servi par `GET /api/v1/invoices/archive`
(paramètres : `keyword`, `department` UUID, `from`/`to` Instant, pagination).
Il n'existe aucun concept de dossier, répertoire ou arborescence dans le code
backend ni frontend (grep "folder|directory|tree" = 0 résultat pertinent).

Le modèle `Invoice` possède déjà un champ `department` (FK), `status` (enum),
`issueDate` (LocalDate), `supplierId` (nullable FK). La facture passe en `ARCHIVE`
automatiquement après paiement (transition state machine PAYE→ARCHIVE). Les factures
archivées sont consultables via `ArchivePage.tsx` avec filtres département/date/texte.

Aucune migration entre V35 (add_payment_status) et V36 n'existe : le prochain
numéro contigu est **V36**.

---

## Décisions de cadrage

1. **Dossiers virtuels admin-gérables** : l'administrateur (ROLE_ADMIN) crée/renomme/
   supprime des dossiers. Les utilisateurs financiers (DAF, ASSISTANT_COMPTABLE)
   peuvent assigner une facture archivée à un dossier.
2. **Un seul niveau** : pas d'arborescence récursive (parent = null ou ID d'un
   dossier parent). Un dossier peut en contenir d'autres (profondeur max : 2,
   appliquée côté service) — suffisant pour un PFE.
3. **Assignment optionnel** : une facture archivée n'a pas besoin d'être dans un
   dossier (nullable FK). La vue sans dossier ("Non classées") reste accessible.
4. **Rétro-compatibilité** : l'endpoint `GET /invoices/archive` est enrichi d'un
   paramètre `folderId` (optionnel, null = toutes), sans casser les appels existants.
5. **Purge safe** : supprimer un dossier ne supprime pas les factures — celles-ci
   redeviennent « Non classées » (SET NULL via contrainte FK).
6. **SoD** : ROLE_ADMIN gère les dossiers (CRUD), DAF/ASSISTANT_COMPTABLE
   assignent les factures, tout le monde voit l'arborescence.

---

## Architecture

### 1. Modèle de données

#### Nouvelle table `archive_folders` (Migration V36)

```sql
CREATE TABLE archive_folders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(500),
    parent_id    UUID REFERENCES archive_folders(id) ON DELETE CASCADE,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (name, parent_id)
);
CREATE INDEX idx_archive_folders_parent ON archive_folders(parent_id);
```

#### Modification de la table `invoices` (Migration V37)

```sql
ALTER TABLE invoices ADD COLUMN folder_id UUID REFERENCES archive_folders(id) ON DELETE SET NULL;
CREATE INDEX idx_invoices_folder ON invoices(folder_id) WHERE folder_id IS NOT NULL;
```

#### Nouvelle entité `ArchiveFolder`

Package : `domain.invoice.model`

```java
@Entity @Table(name = "archive_folders")
@Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ArchiveFolder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ArchiveFolder parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp   private Instant updatedAt;
}
```

#### Modification d'`Invoice`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "folder_id")
private ArchiveFolder folder;
```

#### DTOs

**`ArchiveFolderDTO`** (record) :
- `UUID id`, `String name`, `String description`, `UUID parentId`,
  `String parentName`, `long invoiceCount`, `Instant createdAt`

**`ArchiveFolderCreateRequest`** (record avec validation) :
- `@NotBlank String name`, `String description`, `UUID parentId`

**`ArchiveFolderUpdateRequest`** : mêmes champs que create.

---

### 2. Couche service

Interface `ArchiveFolderService` + implémentation `ArchiveFolderServiceImpl` :

| Méthode | Signature | Règle |
|---------|-----------|-------|
| `createFolder` | `(ArchiveFolderCreateRequest, User) → ArchiveFolderDTO` | profondeur max 2 ; doublon de nom au même niveau → 409 |
| `updateFolder` | `(UUID, ArchiveFolderUpdateRequest, User) → ArchiveFolderDTO` | |
| `deleteFolder` | `(UUID)` | les factures enfants passent à folder=null (via FK CASCADE SET NULL) |
| `listFolders` | `(UUID parentId nullable) → List<ArchiveFolderDTO>` | si parentId null → racine |
| `assignInvoiceToFolder` | `(UUID invoiceId, UUID folderId nullable, User) → void` | folderId null = désassigner ; invoice doit être en ARCHIVE |
| `getFolderTree` | `() → List<ArchiveFolderDTO>` | liste plate triée (racine, puis enfants) |

Repository `ArchiveFolderRepository` (JpaRepository) :
- `findByParentIsNull()` → liste des dossiers racine
- `findByParentId(UUID parentId)` → enfants

`InvoiceService` : ajouter `searchArchived(keyword, department, folderId, from, to, pageable)` —
le folderId null retourne toutes les factures (comportement actuel). Pas de breaking change.

---

### 3. Couche controller

Nouveau `ArchiveFolderController` — mapping `/api/v1/archive/folders` :

| Méthode | Endpoint | Rôle | Retour |
|---------|----------|------|--------|
| GET | `/archive/folders` | DAF, ASSISTANT_COMPTABLE, ADMIN | `List<ArchiveFolderDTO>` (arbre plat) |
| POST | `/archive/folders` | ADMIN | `ArchiveFolderDTO` (201) |
| PUT | `/archive/folders/{id}` | ADMIN | `ArchiveFolderDTO` |
| DELETE | `/archive/folders/{id}` | ADMIN | 204 |
| PATCH | `/archive/invoices/{invoiceId}/folder` | DAF, ASSISTANT_COMPTABLE | `void` 200 |

`InvoiceController.searchArchived` : ajouter `@RequestParam(required=false) UUID folderId`.

---

### 4. Frontend

#### ArchivePage — layout two-column

```
┌────────────────┬────────────────────────────────────────┐
│  Dossiers      │  Résultats (table existante)           │
│  (tree panel)  │                                        │
│                │                                        │
│  📁 Toutes     │  FAC-2026-00001  Acme  10 000 XAF ... │
│  📁 2025       │  FAC-2026-00002  Beta   8 500 XAF ... │
│    📁 Q1       │  ...                                   │
│    📁 Q2       │                                        │
│  📁 Litigieux  │                                        │
└────────────────┴────────────────────────────────────────┘
```

Composant `ArchiveFolderTree.tsx` :
- Panneau gauche fixe (w-48) avec liste des dossiers racine + enfants
- Clic sur un dossier → filtre `folderId` dans l'URL de la query archive
- Bouton « Toutes » (folderId=null) + « Non classées » (folderId=NONE)
- ADMIN : boutons ＋/✎/🗑 sur chaque dossier (inline CRUD)

Modal `AssignFolderModal.tsx` : sélecteur de dossier + PATCH call, visible
sur les lignes de la table archive (bouton 📁 par ligne).

Nouveau hook `useArchiveFolders` → `GET /archive/folders`.

#### i18n (fr.json + en.json — parité)

```json
"archiveFolders": {
  "title": "Dossiers",
  "all": "Toutes les factures",
  "unclassified": "Non classées",
  "new": "Nouveau dossier",
  "edit": "Renommer",
  "delete": "Supprimer",
  "deleteConfirm": "Supprimer ce dossier ? Les factures seront désarchivées.",
  "name": "Nom du dossier",
  "description": "Description (optionnel)",
  "parent": "Dossier parent",
  "none": "Aucun (racine)",
  "assign": "Classer dans un dossier",
  "unassign": "Retirer du dossier",
  "saveError": "Échec de l'enregistrement.",
  "maxDepth": "Profondeur maximale (2 niveaux) atteinte."
}
```

#### backend i18n (messages_fr.properties ISO-8859-1 + messages_en.properties)

- `error.archive.folder.not_found` (FR : Dossier introuvable)
- `error.archive.folder.name_exists` (FR : Un dossier avec ce nom existe d\u00e9j\u00e0 \u00e0 ce niveau)
- `error.archive.folder.max_depth` (FR : Profondeur maximale de 2 niveaux atteinte)
- `error.archive.invoice.not_archived` (FR : La facture n'est pas encore archiv\u00e9e)
- `archive.folder.created`, `archive.folder.updated`, `archive.folder.deleted`
- `archive.invoice.assigned`

---

## Tests

### Backend

**`ArchiveFolderServiceTest`** (MockitoExtension) :
- `createFolder_happy` : dossier créé, DTO retourné
- `createFolder_duplicateName_throwsConflict` : même nom au même niveau
- `createFolder_maxDepth_throwsValidation` : parent lui-même déjà enfant
- `deleteFolder_invoicesBecomUnclassified` : FK SET NULL (vérifié via repo mock)
- `assignInvoiceToFolder_happy` : invoice ARCHIVE, folder valide → sauvegardé
- `assignInvoiceToFolder_notArchived_throwsValidation` : invoice en SOUMIS

**`ArchiveFolderControllerTest`** (SpringBootTest ou WebMvcTest) :
- `POST /archive/folders` ADMIN → 201
- `POST /archive/folders` DAF → 403
- `DELETE /archive/folders/{id}` ADMIN → 204
- `PATCH /archive/invoices/{id}/folder` ASSISTANT_COMPTABLE → 200
- `PATCH /archive/invoices/{id}/folder` ADMIN → 403 (SoD : admin ne touche pas aux données financières)

### Frontend

**`ArchiveFolderTree.test.tsx`** :
- rend la liste des dossiers
- filtre par dossier au clic
- bouton "Toutes" remet folderId à null

---

## Hors-scope

- Déplacement en masse de factures entre dossiers (drag & drop).
- Sous-dossiers à plus de 2 niveaux de profondeur.
- Permissions par dossier (contrôle d'accès par dossier spécifique).
- Import/export de la structure des dossiers.
