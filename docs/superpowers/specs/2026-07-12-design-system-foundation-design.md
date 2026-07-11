# Design System — Lot 1 : Fondation + primitives partagées

**Date :** 2026-07-12
**Branche :** `feat/design-system-foundation` (depuis `main`)
**Statut :** Spec validée en brainstorming, prête pour writing-plans
**Skills mobilisés :** `frontend-design` (direction visuelle), `dataviz` (thème charts),
`ui-ux-pro-max` (base de données design : palettes, typo, charts, guidelines UX),
`design-system` (architecture tokens 3 couches).

---

## 1. Contexte et problème

Le frontend OCT (`invoice-system/frontend`, React 19 + Vite + Tailwind 3.4) possède
déjà un design system substantiel mais **incohérent sur deux plans** :

1. **Doublon de tokens.** `src/index.css` fait coexister DEUX vocabulaires :
   - les tokens **shadcn** (`--background`, `--card`, `--primary`, `--border`,
     `--muted`…) avec des valeurs **cool/slate codées en dur** et **sans override
     `.dark`** (bug latent : `--card` = `#FFFFFF` rendrait blanc-sur-blanc en dark,
     ce que le composant `Panel` contourne déjà en utilisant un `--surface` dédié) ;
   - les tokens **Registre** (Track B) **warm-neutrals** (`--ground`, `--surface`,
     `--hairline`, `--ink*`) + sémantiques (`--pos/--warn/--hot/--crit/--info`
     + `-bg`) + `--gold-deep`, avec un **dark mode designé et vérifié WCAG AA**.

2. **Primitives manquantes.** Seuls 6 composants partagés existent dans
   `src/components/ui/` (`Panel`, `KpiBand`, `StatusBadge`, `Skeleton`,
   `ConfirmDialog`, `ExportMenu`). Il n'y a **pas** de `Button`, `Input`, `Table`,
   `Card`, `Select`, `Tabs` partagés → les styles sont répétés inline dans ~58 pages
   (`px-4 py-2 rounded`, `bg-oct-navy` dispersés). C'est la dette de cohérence.

3. **Charts non thématisés.** Le Dashboard utilise Recharts brut (tooltip par défaut,
   axes/grille non alignés sur le design system). La page Archive (dark) représente
   le « bon » niveau ; le Dashboard (light, Recharts brut) le « à améliorer ».

### Objectif du lot

**Unifier et étendre l'existant, sans repartir de zéro et sans redesigner aucun
écran.** Ce lot pose la fondation ; l'adoption des primitives dans les pages est un
lot ultérieur.

---

## 2. Identité visuelle (décision brainstorming)

**Raffiner en gardant l'ADN OCT**, pas de refonte. C'est déjà une direction
*spécifique au client* (terminal portuaire, finance institutionnelle, CEMAC/Gabon)
et n'est aucun des trois clichés « design IA » (cream+serif+terracotta /
near-black+accent acide / broadsheet hairlines).

- **Navy `#0F2540` = structure** : sidebar, headers, actions structurantes, focus ring.
- **Gold `#C8A84B` / `--gold-deep` = accent rare** : dépensé à un seul endroit
  (CTA premium), jamais en aplats. Discipline `frontend-design` : « spend your
  boldness in one place ».
- **Warm-neutrals Registre = base** : fonds, surfaces, hairlines, encres.
- **Sémantiques réservées au statut** (`pos/warn/hot/crit/info`) — jamais recyclées
  comme couleur de série de graphique (non-négociable `dataviz`).

**Typographie (piste à confirmer, hors périmètre strict de ce lot) :** la base
`ui-ux-pro-max` recommande pour le contexte finance/institutionnel **IBM Plex Sans**
(« Financial Trust », excellent pour la donnée) ou **Lexend** (« Corporate Trust »,
accessibilité). Non figé ici — noté pour un lot typo dédié ; ce lot ne change pas
les fontes.

---

## 3. Section 1 — Unification des tokens

**Stratégie (décision brainstorming) : Registre = source unique, shadcn ré-aliasé
dessus.** Zéro casse : les composants existants continuent d'utiliser `bg-background`,
`bg-card`, `text-primary`… mais ces variables pointent désormais vers le warm Registre.

### Mapping `:root` (light) — dans `src/index.css`

| Variable shadcn | Avant (en dur) | Après |
|---|---|---|
| `--background` | `210 20% 98%` | `var(--ground)` |
| `--foreground` | `213 64% 16%` | `var(--ink)` |
| `--card` | `0 0% 100%` | `var(--surface)` |
| `--card-foreground` | `215 25% 15%` | `var(--ink)` |
| `--popover` | `0 0% 100%` | `var(--surface)` |
| `--popover-foreground` | `215 25% 15%` | `var(--ink)` |
| `--border` | `214 20% 88%` | `var(--hairline)` |
| `--input` | `214 20% 88%` | `var(--hairline)` |
| `--muted` | `210 15% 94%` | `var(--hairline)` |
| `--muted-foreground` | `215 16% 47%` | `var(--ink-soft)` |
| `--secondary` | `210 20% 94%` | `var(--hairline)` |
| `--secondary-foreground` | `213 40% 20%` | `var(--ink)` |
| `--ring` | `213 64% 16%` | `var(--oct-navy)` (structure) |
| `--primary` | `213 64% 16%` | inchangé (navy = identité) |
| `--primary-foreground` | `42 53% 80%` | inchangé |
| `--accent` | `42 53% 54%` | inchangé (gold ; usage discipliné) |
| `--destructive` | `0 84% 60%` | `var(--crit)` (aligne sur la sémantique Registre) |
| `--destructive-foreground` | `0 0% 98%` | inchangé |

> `--primary`/`--accent` restent en valeurs HSL brutes (navy/gold) et NE sont pas
> ré-aliasés : ils portent l'identité et doivent survivre au thème. Seuls les
> neutres et `--destructive` migrent vers Registre.

### Bloc `.dark` manquant (à AJOUTER)

Le bloc `.dark` actuel ne redéfinit QUE les tokens Registre. Point clé CSS : un
`var(--ground)` écrit comme valeur de `--background` dans `:root` est résolu **au
moment de l'usage** selon la cascade — donc sous `.dark`, `--background` reprend
automatiquement la valeur `.dark` de `--ground`. **Ré-aliaser les variables shadcn
une seule fois dans `:root` avec `var()` suffit** ; elles suivent le dark sans
duplication. Le bug latent (absence de `.dark` shadcn) est ainsi résolu par
construction.

Seule exception : `--primary`/`--accent` portent des HSL brutes (navy/gold) qui ne
pointent vers aucun token Registre. Il faut donc leur donner une valeur dark dédiée
sous `.dark` (navy s'assombrit mal sur fond sombre → navy éclaircie ; gold →
`--gold-deep` raised déjà présent), sinon ils resteraient en valeur light en dark.

### Garanties anti-casse

- Aucune classe `oct-*` de Tailwind touchée (`bg-oct-navy`, `text-oct-gold`…).
- `body { @apply bg-background text-foreground }` → le fond devient warm partout
  automatiquement (effet voulu, cohérent avec Archive).
- **Vérification obligatoire** : rendu visuel des écrans clés en light ET dark
  (Dashboard, Archive, une page formulaire, une page tableau) avant de figer.

---

## 4. Section 2 — Primitives partagées

**Emplacement :** `src/components/ui/`.
**Idiome maison confirmé (décision) : natif stylé, ZÉRO nouvelle dépendance.**
Le projet n'a AUCUNE dépendance Radix/Headless ; `ConfirmDialog` est fait main
(`createPortal`). On s'aligne : `cva` + `clsx` + `tailwind-merge` (déjà installés),
`cn()` depuis `@/lib/utils`, radius 4px façon `Panel`, `forwardRef` pour l'interop
formulaire. Toutes bilingue-safe (aucun texte en dur ; les libellés viennent des
consommateurs via i18n).

### a) `Button`
- Variants (`cva`) : `primary` (navy) · `secondary` (surface + hairline) ·
  `ghost` (transparent, hover hairline) · `destructive` (→ `crit`) ·
  `gold` (CTA premium rare, `--gold-deep` — SEUL endroit où le gold vit en fond).
- Sizes : `sm` / `md` / `lg` · `icon`.
- États : `disabled`, `loading` (spinner + `aria-busy`).
- Focus visible obligatoire (ring `--ring` navy).

### b) `Input`
- `bg-surface`, `border-hairline`, texte `text-ink`, placeholder `text-ink-faint`,
  focus ring `--ring`. État erreur → bordure/ring `crit` + `aria-invalid`.

### c) `Select`
- `<select>` natif stylé (pas de dropdown custom, pas de Radix). Même grammaire
  visuelle que `Input`. Chevron via background SVG inline.

### d) `Table`
- Set de sous-composants : `Table`, `THead`, `TBody`, `TR`, `TH`, `TD`.
- En-tête `text-ink-soft`, uppercase fin ; lignes séparées `border-hairline` ;
  hover `hairline/40` ; montants monétaires via la classe `.num` (tabular-nums,
  déjà définie dans index.css). Zéro logique métier ; conteneur `overflow-x-auto`.

### e) `Card` + `Tabs`
- `Card` : généralise `Panel` avec `CardHeader` / `CardTitle` / `CardContent` /
  `CardFooter`. **`Panel` conservé comme alias rétro-compatible** → zéro casse sur
  les usages existants.
- `Tabs` : maison, `role="tablist"` / `aria-selected`, navigation clavier (flèches
  gauche/droite, Home/End). Onglet actif = sous-ligne navy (ou fond surface).

### Hors périmètre Section 2
- Pas de `Dialog` (ConfirmDialog existe déjà, maison).
- Pas de Radix / Headless UI.

---

## 5. Section 3 — Charts Recharts thématisés

**Recharts reste la librairie** (pas de remplacement). On ajoute une couche de thème.
Non-négociables `dataviz` appliqués : **un seul axe Y** (jamais dual-axis) · légende
présente dès 2 séries · grille/axes recessifs · **texte en tokens d'encre, jamais la
couleur de série** · dark = steps re-choisis validés contre la surface dark (pas un
flip auto).

### a) `src/lib/chart-theme.ts`
- Lit les tokens via CSS vars (`--ink-soft`, `--ink-faint`, `--hairline`,
  `--surface`) pour que light/dark suivent le `.dark` sans couleurs en dur.
- Exporte les props par défaut pour `CartesianGrid` (lignes fines recessives),
  `XAxis`, `YAxis` (tick `ink-faint`, axe `hairline`).

### b) Palette catégorielle DÉDIÉE aux séries
- **Distincte des couleurs de statut** (règle non-négociable : `pos/warn/hot/crit`
  restent réservées au statut, jamais « série 4 »).
- Base d'ordre fixe (jamais cyclée) : navy → gold-deep → info → hues additionnels
  dérivés. Au-delà de 8 séries : « Other » / small multiples, jamais une hue générée.
- **Validation par script obligatoire avant de figer** : croiser
  `dataviz/scripts/validate_palette.js` (CVD ≥ 12, contraste, bandes de clarté) en
  mode light ET dark, et `ui-ux-pro-max/scripts/search.py --domain color`. On ne
  valide PAS la palette à l'œil.

### c) `ChartTooltip` (maison, thématisé)
- Remplace la tooltip Recharts par défaut : `bg-surface`, `border-hairline`,
  `text-ink`, montants en `.num`, pastille de série. Passé via
  `content={<ChartTooltip/>}`.

### d) Wrappers légers (optionnels)
- `LineChartThemed` / `BarChartThemed` pré-câblant grille + axes + tooltip depuis
  le thème, pour supprimer la répétition. Recharts reste dessous.

### Périmètre strict
- On crée le thème + les primitives chart + on **valide la palette par script**.
- On **ne redesigne aucun dashboard** dans ce lot ; on branche un mini-exemple de
  rendu pour vérification visuelle (étape 7 `dataviz` : « render it and look at it »).

---

## 6. Ce qui est HORS périmètre (rappel explicite)

- Aucun écran/dashboard/page redesigné (adoption = lot ultérieur).
- Pas de changement de fonte (piste IBM Plex / Lexend notée pour un lot typo).
- Pas de nouvelle dépendance (ni Radix, ni Headless, ni lib de charts).
- Pas de `Dialog` (existe déjà).

---

## 7. Critères d'acceptation

- [ ] `index.css` : shadcn ré-aliasé vers Registre en `:root` ; bloc `.dark`
      complété pour les variables shadcn ; `--primary`/`--accent`/oct-* intacts.
- [ ] Rendu visuel vérifié en light ET dark sur 4 écrans témoins (Dashboard,
      Archive, une page formulaire, une page tableau) — aucune régression
      (pas de blanc-sur-blanc, contrastes AA tenus).
- [ ] Primitives créées et testées : `Button`, `Input`, `Select`, `Table`,
      `Card` (+ `Panel` alias), `Tabs`. Chacune : variants `cva`, `forwardRef`,
      focus visible, bilingue-safe, test unitaire (vitest).
- [ ] `chart-theme.ts` + `ChartTooltip` + palette séries **validée par script**
      (light + dark) ; wrappers si retenus.
- [ ] `vitest` : 100 % vert (aucun échec « pré-existant » toléré).
- [ ] `tsc` : 0 erreur.
- [ ] Backend `./mvnw test` : inchangé (ce lot est front-only) — 574/574.
- [ ] Un commit par tâche, message `type(scope): description`.

---

## 8. Découpage en tâches (pour writing-plans)

1. **T1 — Unification tokens** (`index.css`) + vérification rendu light/dark.
2. **T2 — `Button`** (+ test).
3. **T3 — `Input` + `Select`** (+ tests).
4. **T4 — `Table`** (sous-composants + test).
5. **T5 — `Card` (+ alias `Panel`) + `Tabs`** (+ tests).
6. **T6 — `chart-theme.ts` + palette séries validée par script + `ChartTooltip`
   + wrappers** (+ test + rendu témoin).

Chaque tâche : commit dédié + gate vert (vitest + tsc) avant la suivante.
