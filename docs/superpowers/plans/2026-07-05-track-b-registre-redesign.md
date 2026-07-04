# Track B — Refonte UI « Registre » Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is a **visual design-system** refonte — load superpowers:frontend-design before styling work (aesthetic direction, typography, non-templated choices).

**Goal:** Reconstruire un design system « Registre » cohérent (navy `#0F2540` / or `#C8A84B` disciplinés, neutres chauds, sémantiques d'état désaturées, montants/refs en `tabular-nums`, filets fins plutôt que cartes flottantes, mode sombre **conçu**) — d'abord les tokens, puis les composants partagés, le layout, les écrans à fort trafic, enfin le reste des pages. Direction validée en session précédente ; reconstruction à partir de la spec de tokens (la maquette `oct-registre.html` d'origine n'est plus disponible).

**Architecture:** Frontend uniquement — React 19 + TypeScript + Tailwind + tokens CSS shadcn (HSL-triplet). **Aucun changement backend, aucune migration Flyway.** Le système de couleur reste **unique** : on étend le format shadcn HSL-triplet existant (`--primary: 213 64% 16%`), on n'introduit **pas** un second système. Chaque lot est un commit cohérent, validé par l'utilisateur avant le suivant (cadence lot-par-lot).

**Tech Stack:** React 19, TypeScript, Tailwind CSS, tokens CSS custom (`src/index.css` + `tailwind.config.js`), Vitest + Testing Library, i18next.

## Global Constraints

- **Gate de fin de tâche :** `npx tsc --noEmit` (0 erreur) + `npx vitest run` (0 échec). Backend intouché → `./mvnw test` inutile SAUF si un lot touche par erreur du backend (ne devrait pas). Mémoire `no-failures-on-task-completion` : pas d'excuse « pre-existing ».
- **Système de couleur unique :** tokens en HSL-triplet façon shadcn (`--primary: 213 64% 16%`). Exposer les nouveaux tokens comme couleurs Tailwind (`text-pos`, `bg-warn-bg`, etc.) via `tailwind.config.js`. Ne PAS créer un second système parallèle.
- **`data-status` sur `StatusBadge` NE DOIT PAS être cassé** — sélecteur réservé aux e2e (aucun test actuel ne l'utilise, mais c'est un contrat). Conserver les 3 variantes (`pill`/`dot-only`/`inline`).
- **i18n :** toute chaîne visible passe par `t()` dans les deux langues (`fr.json`/`en.json` UTF-8, doivent rester symétriques : `node` flatten compare = 0 asymétrie). Le redesign ne doit PAS re-figer de chaînes (recoupe A15/A16 déjà faits en Track A).
- **Ne pas re-toucher la LOGIQUE d'`ExportMenu.tsx`** (catch d'erreur ajouté en Track A / MAJEUR-7) — style uniquement.
- **Ne pas redessiner la STRUCTURE** des pages en B5 — juste appliquer tokens + composants partagés.
- **Mode sombre conçu, pas inversé :** défini dès B1 dans le bloc `.dark {}`, chaque composant/écran stylé light+dark au fur et à mesure (décision utilisateur 2026-07-05). Vérifier le rendu dans les deux thèmes à chaque lot.
- **Commits :** un par lot, message `feat(ui): …`, jamais `--no-verify`. **Push :** seulement sur validation explicite (proposer un push dès 10 commits non poussés — mémoire `push-every-10-commits`).
- **Aucune migration Flyway** (dernière migration du projet = **V44**, Track A). Track B est 100% frontend.
- **Validation utilisateur lot-par-lot** (mémoire `per-task-commit-and-handoff`) : après chaque lot → commit → émettre un resume-prompt paste-ready pour le lot suivant.

## État réel vérifié (2026-07-05, avant plan)

- `src/index.css` `:root` n'a QUE `--primary`/`--primary-foreground` en HSL-triplet (les tokens Registre `--ground`/`--hairline`/`--ink`/`--pos`/`--gold-deep`/`.num` **n'existent pas encore** → B1 = travail neuf). Pas de bloc `.dark {}` conçu.
- `StatusBadge.tsx` : palette 9-couleurs Tailwind (slate/blue/amber/orange/teal/green/emerald/red), 3 variantes, `data-status` présent. **Aucun test** ne référence `data-status` aujourd'hui.
- KPI : pas de `KpiBand` — pattern KpiCard isolé dans `DashboardPage.tsx`, plus `AgingBucketsWidget.tsx`, `ReportsPage.tsx`.
- Tous les fichiers cités par la spec (Skeleton, ExportMenu, AppShell, Sidebar, Header, DashboardPage, InvoiceListPage) existent.
- Baseline gate à l'entrée de Track B : `tsc` 0, `vitest` 109/109 (fin Track A).

---

## Task 1 (Lot B1) : Tokens Registre + mode sombre + utilitaire `.num`

**Why:** Fondation de tout le reste. Sans les tokens, chaque composant ré-invente des couleurs Tailwind froides. Le mode sombre doit être conçu ici (pas inversé plus tard) pour éviter les reprises.

**Files:**
- Modify: `frontend/src/index.css` (`:root` + nouveau bloc `.dark`)
- Modify: `frontend/tailwind.config.js` (exposer les tokens comme couleurs/utilitaires)
- Référence (ne pas modifier ici) : la liste de tokens en §B1 de la spec.

**Interfaces:**
- Produces: tokens CSS `--ground`, `--hairline`, `--hairline-strong`, `--ink`, `--ink-soft`, `--ink-faint`, sémantiques `--pos`/`--warn`/`--hot`/`--crit`/`--info` (+ `*-bg`), `--gold-deep`, utilitaire `.num` (mono + `tabular-nums`). Bloc `.dark {}` avec valeurs conçues (fond `#12151A`, surface `#171B22`, filets `#262B34`/`#333A45`, accents or remontés).
- Consumes: rien (feuille de base).

- [ ] **Step 1: Convertir la palette spec en HSL-triplet.** Pour chaque hex de la spec (§B1), calculer le triplet HSL `H S% L%` (format shadcn, sans `hsl()`). Documenter la table hex→HSL en commentaire dans `index.css` pour traçabilité.
- [ ] **Step 2: Ajouter les tokens `:root`** (neutres chauds + sémantiques + `*-bg` + `--gold-deep`). Garder `--primary`/`--primary-foreground` existants.
- [ ] **Step 3: Écrire le bloc `.dark {}` conçu** (pas une inversion — valeurs sombres de la spec B0 : fond/surface/filets dédiés, or remonté).
- [ ] **Step 4: Ajouter l'utilitaire `.num`** (`font-family: <mono stack>; font-variant-numeric: tabular-nums;`) dans `index.css`.
- [ ] **Step 5: Exposer les tokens dans `tailwind.config.js`** (`colors: { ground, hairline, ink, pos, 'pos-bg', warn, … }` en `hsl(var(--x))`) pour permettre `text-pos`/`bg-warn-bg`/`border-hairline`.
- [ ] **Step 6: Test de non-régression tokens.** Ajouter un petit test (ou snapshot ciblé) OU au minimum un composant témoin `TokenSwatch` monté en test qui lit `getComputedStyle` sur une classe `bg-ground`/`text-pos` et vérifie une valeur non-vide en light ET après `document.documentElement.classList.add('dark')`. (But : garantir que `.dark` remappe bien, pas juste que la classe existe.)
- [ ] **Step 7: Gate + commit.** `npx tsc --noEmit` (0), `npx vitest run` (0). Commit `feat(ui): design-system tokens Registre (neutres chauds + sémantiques + dark concu + .num)`.

---

## Task 2 (Lot B2) : Composants partagés — StatusBadge, KpiBand, Skeleton, panel générique, ExportMenu (style), AgingBuckets

**Why:** Les composants réutilisés partout doivent adopter les tokens avant les écrans, sinon on migre les écrans deux fois. `StatusBadge` est le plus sensible (mapping N1/N2, `data-status`).

**Files:**
- Modify: `frontend/src/components/ui/StatusBadge.tsx`, `frontend/src/components/ui/Skeleton.tsx`, `frontend/src/components/ui/ExportMenu.tsx` (style only), `frontend/src/components/dashboard/AgingBucketsWidget.tsx`
- Create: `frontend/src/components/ui/KpiBand.tsx` (bande KPI unifiée, filets internes), `frontend/src/components/ui/Panel.tsx` (carte/panel générique : border fin, radius 4px, ombre plate)
- Create/Modify tests: `StatusBadge.test.tsx` (mapping sémantique + `data-status` préservé), `KpiBand.test.tsx`, `Panel.test.tsx`

**Interfaces:**
- `StatusBadge` : mapping spec B2 — `BROUILLON`→neutre, `SOUMIS`→info, `EN_VALIDATION_N1`→**warn**, `EN_VALIDATION_N2`→**hot**, `VALIDE`→pos (clair), `BON_A_PAYER`→pos, `PAYE`→pos (saturé), `ARCHIVE`→neutre, `REJETE`→crit. **Distinction N1/N2 préservée** (warn vs hot). 3 variantes + `data-status` inchangés.
- `KpiBand` : props `items: {label, value, hint?, tone?}[]` — un conteneur, séparateurs verticaux internes (pas 4 cartes à ombre).
- `Panel` : wrapper générique remplaçant les `bg-white rounded-xl border` répétés.

- [ ] **Step 1: StatusBadge — remapper la palette vers les 5 sémantiques** (tokens `--pos/--warn/--hot/--crit/--info` + neutres). Conserver `pill`/`dot-only`/`inline` et `data-status={status}`.
- [ ] **Step 2: Test StatusBadge (TDD).** Assertions : chaque statut rend la bonne classe sémantique ; `data-status` toujours présent ; N1 ≠ N2 (warn vs hot, classes distinctes) ; les 3 variantes rendent. RED d'abord contre l'ancienne palette.
- [ ] **Step 3: Créer `KpiBand`** (bande unifiée, `.num` sur les valeurs, filets internes, light+dark) + test.
- [ ] **Step 4: Créer `Panel`** (filet fin, radius 4px, ombre quasi-plate, light+dark) + test.
- [ ] **Step 5: Skeleton** — shimmer sur neutres chauds (tokens).
- [ ] **Step 6: ExportMenu** — style Registre uniquement (NE PAS toucher la logique de catch Track A). Vérifier qu'aucune assertion de `ExportMenu.test.tsx` ne casse.
- [ ] **Step 7: AgingBucketsWidget** — filets + `.num`. Vérifier `AgingBucketsWidget.test.tsx` vert.
- [ ] **Step 8: Gate + commit.** tsc 0, vitest 0. Commit `feat(ui): composants partages Registre (StatusBadge semantique, KpiBand, Panel, Skeleton, ExportMenu style)`.

---

## Task 3 (Lot B3) : Layout — AppShell, Sidebar, Header

**Why:** Le châssis encadre tous les écrans ; le migrer maintenant donne le fond `--ground` et le style de nav corrects avant les dashboards.

**Files:**
- Modify: `frontend/src/components/layout/AppShell.tsx` (`bg-gray-50` dur → `bg-ground`), `frontend/src/components/layout/Sidebar.tsx`, `frontend/src/components/layout/Header.tsx`

**Interfaces:**
- `AppShell` : fond via token `--ground` (light) / valeur sombre conçue (dark).
- `Sidebar` : cohérence état actif (`.oct-nav-active`) ; « Système opérationnel » déjà routé via `t()` en Track A (A16) — vérifier, ne pas re-figer.
- `Header` : breadcrumb déjà i18n en Track A (A16) — appliquer le style Registre sans re-toucher la logique de clés.

- [ ] **Step 1: AppShell** — remplacer `bg-gray-50` par `bg-ground` ; vérifier light+dark.
- [ ] **Step 2: Sidebar** — état actif cohérent (or = marque), filets, dark conçu. Confirmer par grep que « Système opérationnel » passe déjà par `t()` (sinon le router, mais ça devrait être fait en A16).
- [ ] **Step 3: Header** — style Registre (eyebrow/breadcrumb en petites-majuscules, `--ink-faint`), sans changer les clés i18n.
- [ ] **Step 4: Gate + commit.** tsc 0, vitest 0 (Header.test/Sidebar tests verts). Commit `feat(ui): layout Registre (AppShell ground token, Sidebar/Header style)`.

---

## Task 4 (Lot B4) : Écrans à fort trafic

**Why:** Le cœur de l'usage quotidien. C'est là que la signature « tabular-nums + filets + chips 2-couleurs » se voit le plus.

**Files (dans cet ordre) :**
- `frontend/src/pages/DashboardPage.tsx` (4 variantes de rôle — utiliser `KpiBand`)
- `frontend/src/pages/InvoiceListPage.tsx`, `frontend/src/pages/InvoiceDetailPage.tsx`
- `frontend/src/pages/PaymentsPage.tsx`
- `frontend/src/pages/ApprovalQueuePage.tsx` (déjà touché en Track A / Task 2 — style ici)
- `frontend/src/pages/matching/MatchingListPage.tsx`, `frontend/src/pages/matching/MatchingDetailPage.tsx`
  *(vérifier les chemins réels de ces 2 fichiers matching avant de commencer — la spec suppose `matching/`.)*

**Interfaces:**
- Tables : filets horizontaux fins, header petites-majuscules sur `--ground`, hover teinté or (~5% via `color-mix`), montants/refs en `.num` alignés à droite, devise en gris clair à côté du nombre.
- Dashboards : `KpiBand` remplace les KpiCard isolés.
- Chips d'état : 2 couleurs max par écran (sémantique + neutre).

- [ ] **Step 1: Vérifier les chemins matching** (`grep -rn "MatchingListPage\|MatchingDetailPage" src` — la Task M5 #1 a créé une page /matching ; confirmer les noms/chemins réels avant de styler).
- [ ] **Step 2: DashboardPage** — `KpiBand`, `Panel`, `.num`, light+dark, pour les 4 variantes de rôle. Vérifier qu'aucun test dashboard ne casse.
- [ ] **Step 3: InvoiceListPage + InvoiceDetailPage** — tables filets, `.num`, chips 2-couleurs, hover or.
- [ ] **Step 4: PaymentsPage** — idem (attention aux formats devise/date déjà faits en A15 : style typographique seulement, ne pas re-toucher la logique de formatage).
- [ ] **Step 5: ApprovalQueuePage** — appliquer le style (logique Track A intacte).
- [ ] **Step 6: MatchingListPage + MatchingDetailPage** — tables/comparaison ligne-à-ligne en style Registre.
- [ ] **Step 7: Gate + commit** (possiblement 2 commits si le lot est gros : `feat(ui): dashboards Registre` puis `feat(ui): listes/detail factures+paiements+matching Registre`). tsc 0, vitest 0.

---

## Task 5 (Lot B5) : Reste des pages — admin / supplier / reports

**Why:** Uniformiser une fois le système stabilisé. **Pas de re-structuration** — juste tokens + composants partagés.

**Files:**
- `frontend/src/pages/admin/*` (tous les écrans admin déjà existants — dont ceux touchés en Track A : AdminUsersPage, IntegrationsPage, SecuritySettingsPage, AdminCompliancePage, AdminAnnouncementsPage, ReportBuilderPage — style uniquement, logique ConfirmDialog intacte)
- `frontend/src/pages/supplier/*`
- `frontend/src/pages/ReportsPage.tsx` + composants reports

**Interfaces:**
- Remplacer les `bg-white rounded-xl border` par `Panel` ; badges via `StatusBadge` ; KPI via `KpiBand` ; montants/refs `.num`.

- [ ] **Step 1: admin/** — passe de migration tokens/composants, écran par écran, sans toucher la logique (ConfirmDialog/mutations Track A intactes). Vérifier les tests admin (Header/DocumentUploader/ConfirmDialog… déjà verts) restent verts.
- [ ] **Step 2: supplier/** — idem.
- [ ] **Step 3: reports** — `ReportsPage.tsx` + widgets (`AgingBucketsWidget` déjà fait en B2) au style Registre.
- [ ] **Step 4: Balayage résiduel** — grep `bg-gray-50\|rounded-xl\|hover:shadow-md\|bg-white rounded` restants dans `src/pages` et `src/components` ; migrer ce qui reste vers tokens/`Panel` (hors cas intentionnels documentés).
- [ ] **Step 5: Gate final + commit.** tsc 0, vitest 0 (toute la suite). Commit `feat(ui): migration Registre du reste des pages (admin/supplier/reports)`.

---

## Final : vérification + docs + décision push/PR

- [ ] Gate complet : `npx tsc --noEmit` (0) + `npx vitest run` (tous verts). Vérifier le rendu **light ET dark** sur au moins Dashboard + une liste + un écran admin (playwright/screenshot ou revue manuelle).
- [ ] i18n symétrie fr/en = 0 asymétrie (le redesign ne doit pas avoir ajouté/figé de chaîne).
- [ ] `docs/TASKS.md` : noter Track B terminé (section §A / statut UI). Si un bug a été trouvé+corrigé pendant le redesign → `docs/KNOWN_ISSUES_REGISTRY.md` (PROB-106+, 7 sections).
- [ ] Rapporter le nombre de commits non poussés ; proposer push + PR à l'utilisateur (jamais de push sans accord explicite).
- [ ] Revue finale (opus) sur les lots B1→B5, puis superpowers:finishing-a-development-branch.

## Différés (hors Track B — laisser en `docs/TASKS.md §A`)
- Rechiffrement d'éventuelles données `IntegrationConnector.config` pré-existantes (MAJEUR-13 : décidé « pas de backfill » car aucune donnée seedée — revérifier si des données réelles existent en prod).
- Questions de règles métier §4 de l'audit (VALIDE→REJETE, `ensureWithinApprovalLimit` sur BAP, lecture ADMIN PO/GRN, cloisonnement checklist B-1) — décision métier requise.
- MAJEUR-8 (garde de délégation, PLAUSIBLE) — écrire un test reproduisant le bug avant de corriger.
