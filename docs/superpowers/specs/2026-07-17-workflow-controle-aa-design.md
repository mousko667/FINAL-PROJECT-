# Spec — SoD compte DAF (V47) + etape de controle AA dans le workflow

Date : 2026-07-17
Branche cible : `fix/audit-sod-critiques` (lot 3)
Findings couverts : decisions metier tranchees a l'issue de l'audit exhaustif 2026-07-17

---

## 1. Contexte et etat reel verifie

Deux decisions metier prises par le porteur du projet, verifiees dans le code et en base
avant redaction (regle projet : ne pas croire le prompt de reprise).

**Verification 1 — cumul de roles.** Requete sur `oct_invoice` :

| username | roles |
|---|---|
| aa | ROLE_ASSISTANT_COMPTABLE |
| daf | **ROLE_ASSISTANT_COMPTABLE, ROLE_DAF** |
| (13 autres) | un seul role metier chacun |

`daf` est le **seul** compte cumulant deux roles metier. Violation de separation des taches
(SoD) : le DAF approuve le bon a payer de factures qu'il pourrait avoir saisies lui-meme.

**Verification 2 — workflow reel.** `docs/WORKFLOW.md` §3 et `StateMachineConfig` concordent :

```
[BROUILLON] --submit--> [SOUMIS]
[SOUMIS] --assign_reviewer--> [EN_VALIDATION_N1]     <- le N1 s'AUTO-assigne
[EN_VALIDATION_N1] --reject--> [REJETE]
[EN_VALIDATION_N2] --reject--> [REJETE]
[VALIDE] --reject--> [REJETE]
[REJETE] --resubmit--> [SOUMIS]
```

Il n'existe **aucune etape AA** entre SOUMIS et N1, et **aucun rejet possible depuis SOUMIS**.
La formulation initiale « ajouter l'etape validation/rejet AA a SOUMIS » ne decrit donc pas un
ajout a cote de l'existant : elle implique d'inserer un point de controle en amont de N1.

**Verification 3 — la delegation ne peut pas servir de repli a l'AA.**
`ApprovalDelegation` porte `department_code NOT NULL` et **aucune notion de role**. De plus
`ApprovalServiceImpl.checkRole` derive le code departement du nom du role
(`ROLE_VALIDATEUR_N1_INFO` -> `INFO`) ; pour `ROLE_ASSISTANT_COMPTABLE` cela produirait un
pseudo-departement `ASSISTANT_COMPTABLE` qui n'existe pas -> aucune delegation ne matchera
jamais. L'AA est transverse (il controle toutes les factures, hors departement) : il n'est pas
delegable en l'etat.

> Cette verification a **invalide** une recommandation initiale (« s'appuyer sur la delegation
> comme repli »). Corrigee avant decision : le repli retenu est un second compte AA.

---

## 2. Decisions retenues

| # | Sujet | Decision |
|---|---|---|
| D1 | Cumul SoD sur `daf` | Retirer `ROLE_ASSISTANT_COMPTABLE` **+ garde anti-cumul** |
| D2 | Etape AA | **Nouvel etat `EN_CONTROLE_AA`** (pas de surcharge de SOUMIS) |
| D3 | Goulot AA | **Second compte AA** seede en V47 (la delegation ne peut pas jouer ce role) |
| D4 | Nature du controle AA | **Acte humain simple** : transmettre ou rejeter avec motif |
| D5 | Chemin de rejet AA | `EN_CONTROLE_AA -> REJETE`, puis `RESUBMIT` existant -> `SOUMIS` |

---

## 3. Architecture

### 3.1 Migration V47 (D1 + D3)

V46 est la derniere migration appliquee -> **V47 est libre**.

Trois operations dans `V47__enforce_sod_aa_daf.sql` :

1. `DELETE` de la ligne `user_roles` liant `daf` a `ROLE_ASSISTANT_COMPTABLE`.
2. Seed d'un second assistant comptable `aa2` (mot de passe `Test1234!`, hash BCrypt aligne
   sur les autres comptes de test) — redondance sur le poste de controle.
   Porte le jeu de comptes de test de **15 a 16** : mettre a jour `docs/` et la memoire
   projet en consequence.
3. **Garde anti-cumul en base** : contrainte/trigger rejetant l'insertion d'un couple
   (AA, DAF) pour un meme `user_id`.

> **Pourquoi en base et non en Java :** il n'existe pas de `UserServiceImpl` centralisant
> l'attribution des roles ; les ecritures passent par `User`, `UserMapper` et
> `UserCsvService` (import CSV). Une garde applicative posee sur un seul chemin serait
> contournable par l'import. La contrainte base couvre tous les chemins d'ecriture.

### 3.2 Etat `EN_CONTROLE_AA` (D2)

- `InvoiceStatus` : `EN_CONTROLE_AA("En controle AA", "Under AA review")`, insere entre
  `SOUMIS` et `EN_VALIDATION_N1`.
- `InvoiceEvent` : nouvel evenement `ASSIGN_AA`.
- `StateMachineConfig` : la transition `SOUMIS -> EN_VALIDATION_N1` est **remplacee**
  (pas doublee) — c'est ce qui rend le controle AA obligatoire :

```
[SOUMIS] --assign_aa (AA)--------> [EN_CONTROLE_AA]
[EN_CONTROLE_AA] --assign_reviewer (N1)--> [EN_VALIDATION_N1]
[EN_CONTROLE_AA] --reject (AA, motif)----> [REJETE]
```

- `ApprovalServiceImpl` : la branche `if (status == SOUMIS)` de `assignReviewer` bascule sur
  `EN_CONTROLE_AA` ; nouvelle methode `assignAA` (SOUMIS -> EN_CONTROLE_AA).
- `ApprovalController` : endpoint `/assign-aa`. Le rejet reutilise l'endpoint existant si sa
  garde d'etat l'autorise (a verifier a l'implementation).
- Aucune migration de donnees : aucune facture n'est en `EN_CONTROLE_AA`, et celles en
  `SOUMIS` restent valides (elles attendront desormais l'AA).

### 3.3 Gardes

- `RoleMatchGuard` : etendu a `ASSIGN_AA` (exige `ROLE_ASSISTANT_COMPTABLE`).
- `RejectionReasonGuard` : reutilise tel quel pour le rejet AA (motif obligatoire).
- `ensureNotSubmitter` : reutilise — l'AA qui a soumis la facture ne peut pas la controler.

> **Point de vigilance a l'implementation :** pour les factures saisies en interne, c'est
> l'AA lui-meme qui soumet. `ensureNotSubmitter` pourrait alors bloquer le flux de saisie
> interne. Si le cas se materialise, **remonter au porteur du projet** plutot que de
> contourner la regle SoD.

---

## 4. Gestion des erreurs

| Situation | Comportement attendu |
|---|---|
| N1 tente `assign_reviewer` depuis `SOUMIS` | `WorkflowException` -> cle `error.approval.aa_control_required` |
| Non-AA tente `assign_aa` | `checkRole` -> 403 |
| `daf` (post-V47) tente une action AA | 403 — verifie que V47 a bien pris effet |
| AA rejette sans motif | Refuse par `RejectionReasonGuard` |
| AA controle une facture qu'il a soumise | `error.approval.approver_is_submitter` (existant) |

Le message en dur `"Cannot assign reviewer from state " + status` present dans
`assignReviewer` est remplace par une cle i18n a cette occasion (coherent avec N17).

**i18n** : nouvelles cles `error.approval.*` en FR **et** EN.
`messages_fr.properties` est ISO-8859-1 / pur-ASCII `\uXXXX` -> ajout **uniquement** via
script Python binaire (`open(path,"ab")`), jamais Edit/Write (cf. PROB-107). Les heredocs
bash mangent les `\u` : ecrire le script dans un fichier puis l'executer.

---

## 5. Impact mesure

**Frontend** (7 fichiers applicatifs + types touchent `SOUMIS`) :
`InvoiceActionPanel`, `StatusBadge`, `ApprovalQueuePage`, `DashboardPage`,
`InvoiceDetailPage`, `InvoiceListPage`, `SupplierInvoicesPage`, `types/invoice.ts`.
Le badge et les filtres doivent connaitre le nouvel etat ; `InvoiceActionPanel` expose
l'action AA (transmettre / rejeter).

**Tests backend** (9 fichiers touchent `SOUMIS`/`assignReviewer`) :
`StateMachineTransitionExhaustiveTest` (matrice de transitions — **echouera par
construction** tant que le nouvel etat n'est pas cable ; c'est le test de reference),
`InvoiceStateMachineServiceTest`, `ApprovalServiceTest`, `ApprovalControllerTest`,
`InvoiceServiceTest`, `PaymentServiceTest`, `PaymentControllerTest`,
`BatchPaymentIntegrationTest`, `ReportServiceTest`.

**Tests frontend** : 4 fichiers (`InvoiceActionPanel.startReview`, `InvoiceTimeline`,
`StatusBadge`, `useInvoices`).

Ces echecs sont **attendus et font partie du travail**, pas des regressions.

---

## 6. Strategie de test (TDD)

1. `StateMachineTransitionExhaustiveTest` : ajout de `EN_CONTROLE_AA` a la matrice.
2. `SOUMIS -> EN_CONTROLE_AA` par l'AA (OK) / par un N1 (403) / par le `daf` post-V47 (403).
3. Rejet AA sans motif -> refuse ; avec motif -> `REJETE` ; puis `resubmit` -> `SOUMIS`.
4. Contrainte anti-cumul : insertion AA+DAF sur un meme user -> rejetee par la base.

**Gate obligatoire avant chaque commit** : `./mvnw test` a 0 failure ;
frontend `npx tsc --noEmit` + `npx vitest run`. Regle projet « no failures on task completion ».

**Verification runtime obligatoire** (driver l'app, pas seulement les tests) :
- login `aa` -> controle d'une facture -> etat `EN_CONTROLE_AA` visible ;
- login `daf` -> perte effective des acces AA ;
- rejet AA -> facture en `REJETE` + motif visible ;
- facture portail `FAC-2026-00026` (id `6813b350-6e98-4908-93af-2a60231aea8e`) comme cas de test.
- Rate-limit login : 5 req/min/IP -> espacer les logins, reutiliser les JWT.

---

## 7. Ordre d'execution

| Ordre | Contenu | Commit |
|---|---|---|
| 1 | V47 : retrait role AA du `daf` + seed `aa2` + contrainte anti-cumul | commit isole |
| 2 | Etat `EN_CONTROLE_AA` : enum, event, state machine, service, controller, i18n, front | commit isole |

V47 d'abord : petit, autonome, et le test « le `daf` est refuse » en depend.
Un commit par sous-tache (regle projet). Chaque bug reel corrige est logue dans
`docs/KNOWN_ISSUES_REGISTRY.md` **avant** le commit — prochain numero libre = **PROB-115**.
Le registre contient des octets NUL -> append via heredoc bash, jamais Edit.

---

## 8. Hors perimetre (explicite)

- **Notifications N5/N6** (notifier l'AA a la soumission, notifier le fournisseur au BAP).
  L'AA *doit* etre notifie pour que les factures ne dorment pas en `SOUMIS`, mais ce sujet
  est deja au backlog du lot 3 sous N5/N6 : le traiter ici melangerait deux sujets dans une
  branche (CLAUDE.md §11, « une branche = un sujet »). **Dependance notee.**
- **Refonte de la delegation par role** (`department_code` nullable + `delegated_role`) :
  chantier a part entiere, non requis par les decisions retenues.
- **Garde matching / checklist a l'etape AA** (D4 = acte humain simple) : ecarte.
- `ChecklistService.getById` sans scope N9 : point ouvert distinct, non traite ici.
