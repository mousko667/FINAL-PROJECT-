# Enrichissement couleur de l'UI (dosage « Soutenu ») — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre l'UI générale plus vivante en appliquant un dosage couleur « Soutenu » à quatre zones (en-têtes de page, cartes KPI, boutons, fonds/navigation secondaire), **sans introduire aucune couleur nouvelle** — uniquement les teintes déjà définies dans `index.css` — et en passant par des primitives partagées.

**Architecture:** On ajoute des *tokens d'application* dans `index.css` (`:root` + `.dark`) qui pointent, via `hsl(var(--token-existant))`, vers les couleurs existantes → light/dark suivent automatiquement la cascade `.dark`. On les expose comme classes Tailwind dans `tailwind.config.js`. On enrichit ensuite les primitives partagées (`Button`, `KpiBand`, `Tabs`) et on crée un composant partagé `PageHeader` (dégradé navy + filet or) qui remplace les ~54 `<h1 className="text-2xl font-bold text-ink">` inline des pages. Chaque combinaison texte-sur-fond-teinté est validée WCAG AA (mesure, pas à l'œil) avant d'être figée.

**Tech Stack:** React 19 + TypeScript, Tailwind CSS (tokens HSL via CSS variables), `class-variance-authority` (variants), Vitest + @testing-library/react, Playwright (vérif runtime).

## Global Constraints

- **Zéro couleur nouvelle.** Uniquement les tokens existants d'`index.css` (navy `--oct-navy`/`--oct-navy-light`, or `--oct-gold`/`--gold-deep`, `--info`/`--info-bg`, warm-neutrals, sémantiques `--pos/--warn/--hot/--crit` + leurs `-bg`). Les nouveaux tokens d'application ne font que **référencer** ces couleurs via `hsl(var(...))`.
- **On modifie les primitives, pas les pages** — SAUF la migration explicite des `<h1>` inline vers `<PageHeader>` (Tâches 6a-6d), qui est le seul endroit où les pages sont touchées, et uniquement pour remplacer le titre par le composant partagé.
- **Sémantique intouchable** : `destructive` reste rouge (`--crit`) ; `pos/warn/hot/crit` ne servent qu'au **sens** (statut/alerte), jamais comme décor.
- **Or discipliné** : l'or (`--oct-gold` / `--gold-deep`) reste rare — CTA premium `gold` + filets d'accent. **Jamais en aplat de fond** (hors le bouton `gold` existant).
- **WCAG AA** : contraste ≥ 4.5 pour le texte normal, ≥ 3 pour grand texte / éléments UI, en light **ET** dark, mesuré avant de figer.
- **Gate par tâche** : `cd frontend && npm run test` (vitest 100 % vert) **et** `npx tsc --noEmit` (0 erreur) avant chaque commit. Backend inchangé (front-only) — ne pas lancer `./mvnw`.
- **Un commit par tâche**, message `type(scope): description`.
- **Note toggle thème** : défaut pré-existant de `hooks/useTheme.ts` (hors périmètre) → la vérif dark se fait via `localStorage['oct-theme']='dark'` + reload, pas via le bouton toggle runtime.

---

## File Structure

- `frontend/src/index.css` — **Modify** : ajouter le bloc « Application tokens (Lot couleur) » dans `:root` (light) et `.dark` (dark). 6 tokens pointant vers l'existant.
- `frontend/tailwind.config.js` — **Modify** : mapper les nouveaux tokens en classes utilitaires (`bg-page-tint`, `bg-nav-tint`, `bg-kpi-info`, `from-header-grad-from`, `to-header-grad-to`, `border-header-accent`/`bg-header-accent`).
- `frontend/src/components/ui/PageHeader.tsx` — **Create** : composant partagé d'en-tête de page (dégradé navy + filet or + titre/sous-titre/actions). Remplace le pattern `<div><h1>…</h1><p>…</p></div>` inline.
- `frontend/src/components/ui/Button.tsx` — **Modify** : variants `primary` (dégradé navy), `secondary` (bleu-ardoise `info` teinté), `ghost` (texte info). `gold` et `destructive` inchangés.
- `frontend/src/components/ui/KpiBand.tsx` — **Modify** : la prop `tone` existante applique désormais un **fond** `-bg` + **barre latérale 4px** de la teinte pleine ; nouveau ton `info` par défaut pour les tuiles informatives (fond `--kpi-info-bg`).
- `frontend/src/components/ui/Tabs.tsx` — **Modify** : `TabList` reçoit un fond `--nav-tint` ; onglet actif détaché sur surface + sous-ligne or ; texte actif navy (light) / info (dark).
- Layout de contenu (fond `--page-tint`) — **Modify** : le conteneur qui enveloppe les pages (identifié en Tâche 8).
- `frontend/src/test/components/PageHeader.test.tsx` — **Create**.
- `frontend/src/test/components/Button.test.tsx`, `KpiBand.test.tsx`, `Tabs.test.tsx` — **Modify** : ajouter les assertions des nouveaux styles (les tests vivent dans `src/test/components/`, PAS à côté des composants).
- `frontend/scripts/color/validate-aa.mjs` — **Create** (Tâche 1) : script de validation de contraste AA versionné, réutilisé à chaque tâche qui fige une combinaison.

---

## Task 1 : Tokens d'application + validation AA

Ajoute les 6 tokens d'application dans `index.css` et les mappe en Tailwind. Écrit un script de validation de contraste AA et l'exécute sur toutes les combinaisons prévues ; **ajuste les opacités** si une combinaison échoue avant de figer.

**Files:**
- Modify: `frontend/src/index.css` (bloc `:root` après `--gold-deep: 44 54% 39%;` ligne ~105 ; bloc `.dark` après `--gold-deep: 43 62% 61%;` ligne ~158)
- Modify: `frontend/tailwind.config.js` (objet `colors` de `theme.extend`, après `"gold-deep"`)
- Create: `frontend/scripts/color/validate-aa.mjs`

**Interfaces:**
- Consumes: tokens existants `--oct-navy`, `--oct-navy-light`, `--oct-gold`, `--gold-deep`, `--info`, `--primary` (dark), `--pos-bg/--warn-bg/--crit-bg`.
- Produces: tokens `--header-grad-from`, `--header-grad-to`, `--header-accent`, `--nav-tint`, `--page-tint`, `--kpi-info-bg` (light+dark) ; classes Tailwind `bg-page-tint`, `bg-nav-tint`, `bg-kpi-info`, `from-header-grad-from`, `to-header-grad-to`, `bg-header-accent`.

- [ ] **Step 1 : Écrire le script de validation AA**

Create `frontend/scripts/color/validate-aa.mjs` :

```js
// Validation WCAG AA des combinaisons texte-sur-fond du lot couleur.
// Zéro dépendance : conversion HSL->sRGB->luminance relative + ratio de contraste.
// Les entrées sont les MÊMES valeurs HSL que les tokens d'index.css (source de vérité).

function hslToRgb(h, s, l) {
  s /= 100; l /= 100;
  const k = (n) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return [f(0), f(8), f(4)].map((x) => Math.round(x * 255));
}
// Compose une couleur semi-transparente sur un fond opaque (alpha compositing).
function over(fg, bg, alpha) {
  return fg.map((c, i) => Math.round(c * alpha + bg[i] * (1 - alpha)));
}
function relLum([r, g, b]) {
  const f = (c) => {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
function ratio(a, b) {
  const [la, lb] = [relLum(a), relLum(b)].sort((x, y) => y - x);
  return (la + 0.05) / (lb + 0.05);
}

// --- Tokens (HSL triplets identiques à index.css) ---
const H = {
  octNavy: [213, 64, 16], octNavyLight: [213, 50, 22],
  primaryDark: [213, 45, 42],
  info: [206, 51, 37], infoDark: [207, 56, 64],
  ink: [40, 15, 12], inkDark: [43, 13, 90],
  groundLight: [45, 33, 98], surfaceLight: [0, 0, 100],
  groundDark: [218, 18, 9], surfaceDark: [218, 19, 11],
  goldDeep: [44, 54, 39], goldDeepDark: [43, 62, 61],
  white: [0, 0, 100],
};
const rgb = (t) => hslToRgb(...t);

// Combinaisons à valider : [nom, texte, fond, [alphaFondSurBase, base]?, seuil]
const checks = [
  // En-tête : texte blanc sur dégradé navy (light) — pire cas = extrémité claire navy-light
  ['header text/white on navy-light (light)', rgb(H.white), rgb(H.octNavyLight), null, 4.5],
  // En-tête dark : encre claire sur navy éclaircie (--primary dark)
  ['header text/ink-dark on primary-dark (dark)', rgb(H.inkDark), rgb(H.primaryDark), null, 4.5],
  // page-tint light : ink sur info@0.07 composé sur ground
  ['ink on page-tint (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.07), null, 4.5],
  ['ink-dark on page-tint (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.06), null, 4.5],
  // nav-tint light : ink sur info@0.12 composé sur ground
  ['ink on nav-tint (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.12), null, 4.5],
  ['ink-dark on nav-tint (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.14), null, 4.5],
  // kpi-info light : ink sur info@0.09 composé sur ground
  ['ink on kpi-info (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.09), null, 4.5],
  ['ink-dark on kpi-info (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.10), null, 4.5],
  // bouton secondary : texte info sur surface (light) et sur surface dark
  ['info text on surface (light)', rgb(H.info), rgb(H.surfaceLight), null, 4.5],
  ['info-dark text on surface (dark)', rgb(H.infoDark), rgb(H.surfaceDark), null, 4.5],
  // bouton gold : texte navy-dark sur gold-deep (déjà en usage, on revérifie)
  ['navy-dark on gold-deep (light)', rgb([213, 70, 10]), rgb(H.goldDeep), null, 4.5],
];

let failed = 0;
for (const [name, fg, bg, , threshold] of checks) {
  const r = ratio(fg, bg);
  const ok = r >= threshold;
  if (!ok) failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${r.toFixed(2)}:1  (min ${threshold})  ${name}`);
}
console.log(failed === 0 ? '\nALL CHECKS PASS' : `\n${failed} CHECK(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
```

- [ ] **Step 2 : Lancer le script (baseline des opacités de la spec)**

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: idéalement `ALL CHECKS PASS`. Si une ligne `page-tint`/`nav-tint`/`kpi-info` échoue (contraste texte insuffisant sur le fond teinté), c'est que l'opacité est trop forte : **baisser** l'opacité de la combinaison fautive (ex. `0.12 → 0.10`) dans le script ET retenir cette valeur pour le Step 3, jusqu'à `ALL CHECKS PASS`. Ne PAS baisser un seuil ; ajuster l'opacité.

- [ ] **Step 3 : Ajouter les tokens dans `index.css`**

Dans le bloc `:root`, juste après `--gold-deep: 44 54% 39%;` :

```css
    /* ===================================================================
     * Application tokens (Lot couleur "Soutenu") — ZÉRO couleur nouvelle :
     * chaque valeur pointe vers un token existant via hsl(var(...)). Les
     * opacités ci-dessous sont celles validées AA par scripts/color/validate-aa.mjs.
     * =================================================================== */
    --header-grad-from: hsl(var(--oct-navy));
    --header-grad-to: hsl(var(--oct-navy-light));
    --header-accent: hsl(var(--oct-gold));
    --nav-tint: hsl(var(--info) / 0.12);
    --page-tint: hsl(var(--info) / 0.07);
    --kpi-info-bg: hsl(var(--info) / 0.09);
```

Dans le bloc `.dark`, juste après `--gold-deep: 43 62% 61%;` :

```css
    /* Application tokens — dark (navy éclaircie via --primary, or rehaussé) */
    --header-grad-from: hsl(var(--primary));
    --header-grad-to: hsl(var(--oct-navy-light));
    --header-accent: hsl(var(--gold-deep));
    --nav-tint: hsl(var(--info) / 0.14);
    --page-tint: hsl(var(--info) / 0.06);
    --kpi-info-bg: hsl(var(--info) / 0.10);
```

> Si le Step 2 a fait baisser une opacité, reporter la valeur retenue ici ET dans le commentaire (la source de vérité reste le script + ce bloc, qui doivent coïncider).

- [ ] **Step 4 : Mapper les tokens en classes Tailwind**

Dans `frontend/tailwind.config.js`, objet `colors` de `theme.extend`, juste après la ligne `"gold-deep": "hsl(var(--gold-deep))",` :

```js
        "header-grad-from": "var(--header-grad-from)",
        "header-grad-to": "var(--header-grad-to)",
        "header-accent": "var(--header-accent)",
        "nav-tint": "var(--nav-tint)",
        "page-tint": "var(--page-tint)",
        "kpi-info": "var(--kpi-info-bg)",
```

(Ces tokens sont déjà des couleurs complètes `hsl(...)` / `hsl(... / alpha)`, donc on les référence en `var(--...)` direct, sans re-wrapper dans `hsl()` — contrairement aux triplets bruts comme `--ground`.)

- [ ] **Step 5 : Vérifier que Tailwind génère les classes + gate**

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur (aucun code TS touché, sanity check).

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: `ALL CHECKS PASS` (avec les opacités figées).

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/index.css frontend/tailwind.config.js frontend/scripts/color/validate-aa.mjs
git commit -m "feat(ui): tokens d'application couleur (Soutenu) valides AA"
```

---

## Task 2 : Composant PageHeader (dégradé navy + filet or)

Crée le composant partagé d'en-tête de page qui portera le dégradé navy + filet or. Il ne migre encore aucune page (Tâches 6a-6d) — cette tâche le crée et le teste isolément.

**Files:**
- Create: `frontend/src/components/ui/PageHeader.tsx`
- Create: `frontend/src/test/components/PageHeader.test.tsx`

**Interfaces:**
- Consumes: classes Tailwind `from-header-grad-from`, `to-header-grad-to`, `bg-header-accent` (Tâche 1).
- Produces: `PageHeader` — `export function PageHeader(props: PageHeaderProps)` avec `PageHeaderProps = { title: React.ReactNode; subtitle?: React.ReactNode; actions?: React.ReactNode; className?: string }`. Rend un `<h1>` (le titre reste un `<h1>` pour l'accessibilité et la non-régression des pages), un sous-titre optionnel, une zone d'actions optionnelle à droite, un fond dégradé navy et un filet or de 3px en bas.

- [ ] **Step 1 : Écrire les tests qui échouent**

Create `frontend/src/test/components/PageHeader.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageHeader } from '@/components/ui/PageHeader'

describe('PageHeader', () => {
  it('rend le titre dans un <h1> (accessibilité, pas de texte en dur)', () => {
    render(<PageHeader title="Tableau de bord" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Tableau de bord' })).toBeInTheDocument()
  })

  it('rend le sous-titre quand il est fourni', () => {
    render(<PageHeader title="T" subtitle="Sous-titre" />)
    expect(screen.getByText('Sous-titre')).toBeInTheDocument()
  })

  it('rend la zone actions quand elle est fournie', () => {
    render(<PageHeader title="T" actions={<button>Exporter</button>} />)
    expect(screen.getByRole('button', { name: 'Exporter' })).toBeInTheDocument()
  })

  it('porte le dégradé navy et le filet or (via les tokens d\'application)', () => {
    const { container } = render(<PageHeader title="T" />)
    const root = container.firstChild as HTMLElement
    expect(root.className).toMatch(/from-header-grad-from/)
    expect(root.className).toMatch(/to-header-grad-to/)
    // le filet or est un élément dédié avec bg-header-accent
    expect(container.querySelector('.bg-header-accent')).not.toBeNull()
  })

  it('propage className', () => {
    const { container } = render(<PageHeader title="T" className="mb-8" />)
    expect((container.firstChild as HTMLElement).className).toMatch(/mb-8/)
  })
})
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run: `cd frontend && npm run test src/test/components/PageHeader.test.tsx`
Expected: FAIL (`Cannot find module '@/components/ui/PageHeader'`).

- [ ] **Step 3 : Créer le composant**

Create `frontend/src/components/ui/PageHeader.tsx` :

```tsx
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface PageHeaderProps {
  title: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  className?: string
}

/**
 * En-tête de page partagé (Lot couleur "Soutenu"). Bandeau titre en dégradé navy
 * (--header-grad-from → --header-grad-to) avec un filet or de 3px en bas
 * (--header-accent). Titre en blanc, sous-titre en or atténué. Remplace le pattern
 * `<div><h1 class="text-2xl font-bold text-ink">…</h1><p>…</p></div>` inline des pages.
 * Aucun texte en dur : title/subtitle viennent de l'appelant (i18n côté page).
 */
export function PageHeader({ title, subtitle, actions, className }: PageHeaderProps) {
  return (
    <div
      className={cn(
        'relative rounded-[4px] bg-gradient-to-r from-header-grad-from to-header-grad-to',
        'px-6 py-5',
        className
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-white">{title}</h1>
          {subtitle && (
            <p className="text-sm text-oct-gold-light/90 mt-1">{subtitle}</p>
          )}
        </div>
        {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
      </div>
      {/* Filet or 3px en bas */}
      <div className="absolute inset-x-0 bottom-0 h-[3px] bg-header-accent rounded-b-[4px]" />
    </div>
  )
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils passent + gate**

Run: `cd frontend && npm run test src/test/components/PageHeader.test.tsx`
Expected: PASS (5/5).

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/components/ui/PageHeader.tsx frontend/src/test/components/PageHeader.test.tsx
git commit -m "feat(ui): composant PageHeader (degrade navy + filet or)"
```

---

## Task 3 : Boutons (primary dégradé, secondary bleu-ardoise, ghost info)

Enrichit la primitive `Button` : `primary` en dégradé navy, `secondary` en bleu-ardoise `info` teinté, `ghost` en texte info. `gold` et `destructive` restent inchangés (sémantique + or discipliné).

**Files:**
- Modify: `frontend/src/components/ui/Button.tsx:19-25` (bloc `variant` du `cva`)
- Modify: `frontend/src/test/components/Button.test.tsx` (ajouter assertions secondary/ghost/primary-gradient)

**Interfaces:**
- Consumes: classes Tailwind `oct-navy`, `oct-navy-light`, `info`, `info-bg`.
- Produces: `Button` — mêmes `variant`/`size`/props qu'avant (aucune signature TS modifiée), styles enrichis.

- [ ] **Step 1 : Écrire les tests qui échouent**

Ajouter dans `frontend/src/test/components/Button.test.tsx` (dans le `describe('Button', ...)`) :

```tsx
  it('primary porte un dégradé navy', () => {
    render(<Button variant="primary">x</Button>)
    const c = screen.getByRole('button').className
    expect(c).toMatch(/bg-gradient-to-/)
    expect(c).toMatch(/from-oct-navy-light/)
    expect(c).toMatch(/to-oct-navy/)
  })

  it('secondary porte un fond bleu-ardoise (info teinté) + bordure info', () => {
    render(<Button variant="secondary">x</Button>)
    const c = screen.getByRole('button').className
    expect(c).toMatch(/bg-info-bg/)
    expect(c).toMatch(/border-info/)
    expect(c).toMatch(/text-info/)
  })

  it('ghost porte un texte info', () => {
    render(<Button variant="ghost">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/text-info/)
  })
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run: `cd frontend && npm run test src/test/components/Button.test.tsx`
Expected: FAIL (les nouvelles assertions ne matchent pas les anciennes classes).

- [ ] **Step 3 : Modifier les variants**

Remplacer le bloc `variant` (lignes 19-25) par :

```tsx
      variant: {
        primary:
          'bg-gradient-to-b from-oct-navy-light to-oct-navy text-white hover:to-oct-navy-light',
        secondary:
          'bg-info-bg text-info border border-info/40 hover:bg-info/15',
        ghost: 'bg-transparent text-info hover:bg-info/10',
        destructive: 'bg-crit text-white hover:bg-crit/90',
        gold: 'bg-gold-deep text-oct-navy-dark hover:bg-gold-deep/90',
      },
```

- [ ] **Step 4 : Lancer les tests + gate**

Run: `cd frontend && npm run test src/test/components/Button.test.tsx`
Expected: PASS (dont l'ancien test `applique la variante gold` et `destructive`, inchangés).

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/components/ui/Button.tsx frontend/src/test/components/Button.test.tsx
git commit -m "feat(ui): boutons primary degrade + secondary/ghost info"
```

---

## Task 4 : Cartes KPI (fond `-bg` + barre latérale, ton info par défaut)

Enrichit `KpiBand` : la prop `tone` existante applique désormais un **fond** de la teinte `-bg` + une **barre latérale 4px** de la teinte pleine. Les tuiles sans `tone` explicite prennent le fond informatif `--kpi-info-bg`. La règle « couleur = sens d'alerte » reste pilotée par la page appelante (elle passe `tone`).

**Files:**
- Modify: `frontend/src/components/ui/KpiBand.tsx` (constantes de style + rendu de chaque item)
- Modify: `frontend/src/test/components/KpiBand.test.tsx` (assertions fond + barre)

**Interfaces:**
- Consumes: classes Tailwind `pos-bg/warn-bg/hot-bg/crit-bg/info-bg`, `kpi-info`, et les teintes pleines `pos/warn/hot/crit/info` pour la barre.
- Produces: `KpiBand` — même API (`items: KpiBandItem[]`, `KpiBandItem.tone?: KpiTone`), rendu enrichi. `KpiTone` inchangé (`'pos'|'warn'|'hot'|'crit'|'info'`).

- [ ] **Step 1 : Écrire les tests qui échouent**

Remplacer le contenu de `frontend/src/test/components/KpiBand.test.tsx` par (ou compléter) :

```tsx
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { KpiBand } from '@/components/ui/KpiBand'

describe('KpiBand', () => {
  it('une tuile sans tone prend le fond informatif (bleu-ardoise)', () => {
    const { container } = render(<KpiBand items={[{ label: 'Total', value: 42 }]} />)
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-kpi-info/)
  })

  it('une tuile tone=crit prend le fond crit-bg + barre latérale crit', () => {
    const { container } = render(
      <KpiBand items={[{ label: 'En retard', value: 3, tone: 'crit' }]} />
    )
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-crit-bg/)
    expect(tile.className).toMatch(/border-l-4/)
    expect(tile.className).toMatch(/border-l-crit/)
  })

  it('une tuile tone=pos prend le fond pos-bg + barre latérale pos', () => {
    const { container } = render(
      <KpiBand items={[{ label: 'Conformité', value: 'OK', tone: 'pos' }]} />
    )
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-pos-bg/)
    expect(tile.className).toMatch(/border-l-pos/)
  })

  it('affiche label, valeur et hint', () => {
    const { getByText } = render(
      <KpiBand items={[{ label: 'Total', value: 42, hint: 'ce mois' }]} />
    )
    expect(getByText('Total')).toBeInTheDocument()
    expect(getByText('42')).toBeInTheDocument()
    expect(getByText('ce mois')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run: `cd frontend && npm run test src/test/components/KpiBand.test.tsx`
Expected: FAIL (pas de `data-kpi-tile`, pas de fond par tuile).

- [ ] **Step 3 : Enrichir KpiBand**

Remplacer le corps de `frontend/src/components/ui/KpiBand.tsx` par :

```tsx
import { cn } from '@/lib/utils'

export type KpiTone = 'pos' | 'warn' | 'hot' | 'crit' | 'info'

export interface KpiBandItem {
  label: string
  value: string | number
  hint?: string
  tone?: KpiTone
}

interface KpiBandProps {
  items: KpiBandItem[]
  className?: string
}

// Fond teinté (-bg) + barre latérale 4px de la teinte pleine, par ton.
// Une tuile SANS tone = informative → fond bleu-ardoise --kpi-info-bg + barre info.
const TONE_BG: Record<KpiTone, string> = {
  pos: 'bg-pos-bg border-l-4 border-l-pos',
  warn: 'bg-warn-bg border-l-4 border-l-warn',
  hot: 'bg-hot-bg border-l-4 border-l-hot',
  crit: 'bg-crit-bg border-l-4 border-l-crit',
  info: 'bg-kpi-info border-l-4 border-l-info',
}
const TONE_TEXT: Record<KpiTone, string> = {
  pos: 'text-pos', warn: 'text-warn', hot: 'text-hot', crit: 'text-crit', info: 'text-ink',
}
const DEFAULT_TILE = 'bg-kpi-info border-l-4 border-l-info'

/**
 * Unified KPI band (Track B / Lot B2, enrichi Lot couleur). UN conteneur bordé
 * avec séparateurs internes. Chaque tuile porte un fond teinté selon son `tone` :
 * pos/warn/hot/crit = fond -bg + barre de la teinte pleine (SENS d'alerte, piloté
 * par la page) ; sans tone = fond bleu-ardoise informatif. La couleur ne décore
 * jamais sans signification.
 */
export function KpiBand({ items, className }: KpiBandProps) {
  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row',
        'rounded-[4px] border border-hairline shadow-sm overflow-hidden',
        'divide-y sm:divide-y-0 sm:divide-x divide-hairline',
        className
      )}
    >
      {items.map((item, i) => (
        <div
          key={i}
          data-kpi-tile
          className={cn('flex-1 min-w-0 px-5 py-4', item.tone ? TONE_BG[item.tone] : DEFAULT_TILE)}
        >
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            {item.label}
          </p>
          <p className={cn('num text-2xl font-semibold mt-1', item.tone ? TONE_TEXT[item.tone] : 'text-ink')}>
            {item.value}
          </p>
          {item.hint && <p className="text-xs text-ink-soft mt-1">{item.hint}</p>}
        </div>
      ))}
    </div>
  )
}
```

> Note : le fond global `bg-ground` du conteneur est retiré (chaque tuile a désormais son fond) et `overflow-hidden` est ajouté pour que les barres latérales et les coins arrondis coexistent proprement.

- [ ] **Step 4 : Lancer les tests + gate**

Run: `cd frontend && npm run test src/test/components/KpiBand.test.tsx`
Expected: PASS.

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: `ALL CHECKS PASS` (les combinaisons ink-sur-*-bg des KPI d'alerte étaient déjà validées au Lot B1 ; kpi-info est validé en Tâche 1).

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/components/ui/KpiBand.tsx frontend/src/test/components/KpiBand.test.tsx
git commit -m "feat(ui): KPI fond teinte + barre laterale par tone (sens d'alerte)"
```

---

## Task 5 : Navigation secondaire (Tabs teinté + sous-ligne or)

Enrichit `TabList`/`Tab` : le bandeau d'onglets prend un fond `--nav-tint` ; l'onglet actif se détache sur surface avec une **sous-ligne or** ; texte actif navy (light) / info (dark).

**Files:**
- Modify: `frontend/src/components/ui/Tabs.tsx:80-88` (`TabList`, className) et `frontend/src/components/ui/Tabs.tsx:112-119` (`Tab`, className actif/inactif)
- Modify: `frontend/src/test/components/Tabs.test.tsx` (assertions fond nav + sous-ligne or)

**Interfaces:**
- Consumes: classes Tailwind `nav-tint`, `oct-gold`, `surface`, `info`, `oct-navy`.
- Produces: `Tabs`/`TabList`/`Tab`/`TabPanel` — mêmes signatures et comportement clavier (roving tabindex APG) qu'avant, styles enrichis.

- [ ] **Step 1 : Écrire les tests qui échouent**

Ajouter dans `frontend/src/test/components/Tabs.test.tsx` (dans le `describe` Tabs) :

```tsx
  it('TabList porte le fond bleu-ardoise nav-tint', () => {
    const { container } = render(
      <Tabs defaultValue="a">
        <TabList><Tab value="a">A</Tab><Tab value="b">B</Tab></TabList>
      </Tabs>
    )
    const list = container.querySelector('[role="tablist"]') as HTMLElement
    expect(list.className).toMatch(/bg-nav-tint/)
  })

  it('l\'onglet actif porte une sous-ligne or', () => {
    const { getByRole } = render(
      <Tabs defaultValue="a">
        <TabList><Tab value="a">A</Tab><Tab value="b">B</Tab></TabList>
      </Tabs>
    )
    const activeTab = getByRole('tab', { name: 'A' })
    expect(activeTab.getAttribute('aria-selected')).toBe('true')
    expect(activeTab.className).toMatch(/border-oct-gold/)
  })
```

(Adapter l'import en tête du fichier de test si `TabList`/`Tab` ne sont pas déjà importés.)

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run: `cd frontend && npm run test src/test/components/Tabs.test.tsx`
Expected: FAIL (pas de `bg-nav-tint`, sous-ligne navy et non or).

- [ ] **Step 3 : Enrichir TabList et Tab**

Dans `TabList` (le `return`, lignes ~80-87), remplacer le `className` du `<div role="tablist">` :

```tsx
      className={cn(
        'flex items-center gap-1 rounded-t-[4px] bg-nav-tint px-1 pt-1 border-b border-hairline',
        className
      )}
```

Dans `Tab` (lignes ~112-119), remplacer le bloc `className` du bouton par :

```tsx
      className={cn(
        'px-4 py-2.5 -mb-px border-b-2 text-sm font-medium transition-colors rounded-t-[4px]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        active
          ? 'bg-surface border-oct-gold text-oct-navy dark:text-info'
          : 'border-transparent text-ink-soft hover:text-ink',
        className
      )}
```

- [ ] **Step 4 : Lancer les tests + gate**

Run: `cd frontend && npm run test src/test/components/Tabs.test.tsx`
Expected: PASS (dont les tests clavier APG existants — le comportement roving tabindex n'est pas touché).

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: `ALL CHECKS PASS`.

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/components/ui/Tabs.tsx frontend/src/test/components/Tabs.test.tsx
git commit -m "feat(ui): onglets fond nav-tint + onglet actif sous-ligne or"
```

---

## Task 6a : Fond de page teinté (layout de contenu)

Applique le fond `--page-tint` à la zone de contenu qui enveloppe les pages, en une seule modification de layout.

**Files:**
- Modify: le conteneur de contenu du layout principal (à identifier : `frontend/src/components/layout/` — le wrapper qui contient `<Outlet />` / les pages, à côté de `Header.tsx`).

**Interfaces:**
- Consumes: classe Tailwind `page-tint`.
- Produces: zone de contenu avec fond teinté ; aucune signature modifiée.

- [ ] **Step 1 : Identifier le wrapper de contenu**

Run: `cd frontend && grep -rn "Outlet\|<main" src/components/layout src/App.tsx 2>/dev/null | head`
Repérer le `<main>` (ou le `<div>` de contenu) qui enveloppe les pages sous le `Header`. C'est là qu'on applique le fond.

- [ ] **Step 2 : Appliquer le fond page-tint**

Ajouter `bg-page-tint` à la `className` du conteneur de contenu identifié (en gardant les classes existantes). Ne PAS toucher au fond de la sidebar ni du Header.

- [ ] **Step 3 : Gate**

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: `ALL CHECKS PASS` (ink sur page-tint validé en Tâche 1).

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

Run: `cd frontend && npm run test`
Expected: 100 % vert.

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/components/layout
git commit -m "feat(ui): fond de zone de contenu teinte (page-tint)"
```

---

## Task 6b : Migration des en-têtes de page — écrans témoins (Dashboard, Reports)

Remplace les `<h1>` inline par `<PageHeader>` sur les 2 écrans de la vérif runtime les plus visibles. Sert de patron pour les vagues suivantes.

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx` (ligne ~106 : `<h1 className="text-2xl font-bold text-ink">{t('dashboard.title')}</h1>`)
- Modify: `frontend/src/pages/ReportsPage.tsx` (lignes ~138-141 : `<div><h1>…</h1><p>…subtitle…</p></div>`)

**Interfaces:**
- Consumes: `PageHeader` (Tâche 2).
- Produces: 2 pages migrées ; aucune logique métier touchée.

- [ ] **Step 1 : Migrer Dashboard**

Dans `DashboardPage.tsx`, importer `PageHeader` (`import { PageHeader } from '@/components/ui/PageHeader'`) et remplacer le `<h1 className="text-2xl font-bold text-ink">{t('dashboard.title')}</h1>` (et son éventuel wrapper de titre immédiat) par :

```tsx
<PageHeader title={t('dashboard.title')} />
```

Conserver tout le reste de la page (cartes, sections) intact.

- [ ] **Step 2 : Migrer Reports**

Dans `ReportsPage.tsx`, importer `PageHeader` et remplacer le bloc :

```tsx
<div>
  <h1 className="text-2xl font-bold text-ink">{t('reports.title')}</h1>
  <p className="text-sm text-ink-soft mt-1">{t('reports.subtitle')}</p>
</div>
```

par :

```tsx
<PageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />
```

- [ ] **Step 3 : Gate**

Run: `cd frontend && npx tsc --noEmit` → 0 erreur.
Run: `cd frontend && npm run test` → 100 % vert.

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/pages/DashboardPage.tsx frontend/src/pages/ReportsPage.tsx
git commit -m "feat(ui): PageHeader sur Dashboard et Rapports (ecrans temoins)"
```

---

## Task 6c : Migration des en-têtes de page — vague 2 (pages métier restantes)

Migre les `<h1>` inline restants des pages **hors admin** vers `<PageHeader>`.

**Files:**
- Modify: toutes les pages sous `frontend/src/pages/` (hors `admin/`) contenant `<h1 className="text-2xl font-bold text-ink">`, hors Dashboard/Reports déjà faits.

**Interfaces:**
- Consumes: `PageHeader`.
- Produces: pages métier migrées.

- [ ] **Step 1 : Lister les pages restantes**

Run: `cd frontend && grep -rln 'text-2xl font-bold text-ink' src/pages --include=*.tsx | grep -v '/admin/' | grep -vE 'DashboardPage|ReportsPage'`
Traiter chaque fichier de la liste.

- [ ] **Step 2 : Migrer chaque page**

Pour chaque page, importer `PageHeader` et remplacer le bloc titre inline (soit `<h1 …>title</h1>` seul, soit `<div><h1 …>title</h1><p …>subtitle</p></div>`, soit un `<div class="flex justify-between"><div><h1>…</h1><p>…</p></div><…actions…></div>`) par `<PageHeader title={…} subtitle={…} actions={…} />` en réutilisant EXACTEMENT les mêmes `t(...)` et les mêmes éléments d'action. Ne PAS changer le texte, ni la logique.

> Règle de non-régression : le titre doit rester rendu dans un `<h1>` (PageHeader s'en charge). Si une page a plusieurs `<h1>` (rare), ne migrer que le titre principal de page.

- [ ] **Step 3 : Gate**

Run: `cd frontend && npx tsc --noEmit` → 0 erreur.
Run: `cd frontend && npm run test` → 100 % vert. (Si un test de page assert la présence d'un titre via `getByRole('heading', {level:1})`, il reste vert puisque PageHeader rend un `<h1>`.)

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/pages
git commit -m "feat(ui): PageHeader sur les pages metier (vague 2)"
```

---

## Task 6d : Migration des en-têtes de page — vague 3 (pages admin)

Migre les `<h1>` inline restants des pages **admin** vers `<PageHeader>`.

**Files:**
- Modify: pages sous `frontend/src/pages/admin/` contenant `<h1 className="text-2xl font-bold text-ink">`.

**Interfaces:**
- Consumes: `PageHeader`.
- Produces: pages admin migrées ; plus aucun `<h1 className="text-2xl font-bold text-ink">` inline dans les pages.

- [ ] **Step 1 : Lister les pages admin restantes**

Run: `cd frontend && grep -rln 'text-2xl font-bold text-ink' src/pages/admin --include=*.tsx`
Traiter chaque fichier (certaines pages ont un `<h1 … flex items-center gap-2>` avec une icône — passer l'icône + le titre dans `title={<>…</>}`).

- [ ] **Step 2 : Migrer chaque page admin**

Même règle que Tâche 6c. Pour les titres avec icône (`<h1 className="text-2xl font-bold text-ink flex items-center gap-2"><Icon/>{t(...)}</h1>`), passer `title={<span className="flex items-center gap-2"><Icon aria-hidden />{t(...)}</span>}`.

- [ ] **Step 3 : Vérifier qu'il ne reste plus d'en-tête inline**

Run: `cd frontend && grep -rln 'text-2xl font-bold text-ink' src/pages --include=*.tsx`
Expected: aucune sortie (toutes les pages migrées). Si des occurrences légitimes restent (un `text-2xl font-bold text-ink` qui n'est PAS un titre de page — ex. une grande valeur numérique), les laisser et le noter dans le rapport.

- [ ] **Step 4 : Gate**

Run: `cd frontend && npx tsc --noEmit` → 0 erreur.
Run: `cd frontend && npm run test` → 100 % vert.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/pages/admin
git commit -m "feat(ui): PageHeader sur les pages admin (vague 3)"
```

---

## Task 7 : Vérif runtime (Playwright light + dark) + validation finale

Valide visuellement les 4 zones sur de vraies pages, en light ET dark, sans régression de lisibilité. Front-only : ne pas lancer le backend Maven, mais le backend doit tourner pour servir les données (conteneurs Docker déjà up).

**Files:**
- Aucun fichier de production modifié (tâche de validation ; corrections éventuelles reviennent dans la tâche concernée).

**Interfaces:**
- Consumes: tout le travail des Tâches 1-6d.
- Produces: preuve visuelle + suite verte.

- [ ] **Step 1 : Gate complète front**

Run: `cd frontend && npm run test`
Expected: 100 % vert (0 échec).

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

Run: `cd frontend && node scripts/color/validate-aa.mjs`
Expected: `ALL CHECKS PASS`.

- [ ] **Step 2 : Build + déploiement front (procédure CLAUDE.md §13)**

Run:
```bash
cd frontend && npm run build
docker cp dist/. oct_frontend:/usr/share/nginx/html/
docker exec oct_frontend nginx -s reload
```
(Le backend `oct_backend` doit tourner ; sinon `docker compose up -d`.)

- [ ] **Step 3 : Captures Playwright light + dark**

Se connecter en `daf` / `Test1234!` (SoD : `/reports` = daf, PAS admin) via l'UdI (`http://localhost:3000`). Pour chaque écran témoin — **Dashboard**, **Rapports** (`daf`), une **page tableau** (ex. `/invoices`), une **page formulaire** (ex. `/invoices/new`) — capturer en **light** puis en **dark** (basculer via `localStorage.setItem('oct-theme','dark')` + reload, cf. note toggle spec).

Vérifier visuellement sur chaque capture :
- [ ] En-tête de page = **dégradé navy + filet or 3px**, titre blanc lisible.
- [ ] Tuiles KPI = fond teinté selon le sens (info bleu-ardoise par défaut ; pos/warn/crit seulement là où le chiffre porte un sens d'alerte) + barre latérale ; **jamais bariolé**.
- [ ] Boutons = primary dégradé navy, secondary bleu-ardoise, `gold` rare, `destructive` **rouge**.
- [ ] Fond de contenu = teinte `page-tint` discrète ; onglets = fond `nav-tint`, onglet actif détaché + **sous-ligne or**.
- [ ] Lisibilité AA préservée en dark (pas de texte sombre sur fond sombre, pas de blanc-sur-blanc).

- [ ] **Step 4 : (si défaut visuel) corriger dans la tâche concernée**

Tout écart se corrige dans la Tâche 1-6d correspondante (cycle test→fix→commit), pas ici. Cette tâche ne se termine que quand les 4 écrans sont conformes en light ET dark.

- [ ] **Step 5 : Commit de clôture (si ajustements) ou rien**

Les ajustements sont commités dans leur tâche. Sinon, aucun commit ici.

---

## Self-Review (effectuée)

- **Couverture spec** : tokens d'application + validation AA → T1 ; en-têtes dégradé navy + filet or → T2 (composant) + T6a/b/c/d (application, car aucun composant partagé n'existait — décision commanditaire 2026-07-12 : créer + migrer) ; cartes KPI fond+barre / ton info → T4 ; boutons primary dégradé + secondary/ghost info → T3 ; fonds page-tint + onglets nav-tint + sous-ligne or → T6a + T5 ; vérif runtime light/dark 4 écrans → T7. `destructive` rouge et or discipliné → garantis par T3 (variants inchangés) + garde-fous des tests.
- **Zéro couleur nouvelle** : tous les nouveaux tokens (T1) pointent via `hsl(var(...))` vers des tokens existants ; aucune valeur hex/hsl nouvelle. Vérifié combinaison par combinaison dans `validate-aa.mjs`.
- **Placeholders** : aucun step de code sans code ; les seules parties « à identifier » (wrapper de contenu T6a, liste des pages T6c/d) sont des recherches `grep` explicites, pas des TODO.
- **Cohérence des types** : `PageHeaderProps { title; subtitle?; actions?; className? }` défini en T2 et consommé tel quel en T6b/c/d. `KpiTone`/`KpiBandItem` inchangés (T4). Aucune signature de `Button`/`Tabs` modifiée. Les classes Tailwind nommées en T1 (`page-tint`, `nav-tint`, `kpi-info`, `header-grad-from/to`, `header-accent`) sont exactement celles utilisées en T2/T4/T5/T6a.
- **Écart spec assumé** : la spec supposait « un composant d'en-tête de page partagé » ; il n'existait pas (~54 `<h1>` inline). Décision commanditaire : le créer (T2) + migrer toutes les pages (T6b/c/d). C'est le seul endroit où les pages sont touchées, uniquement pour le titre.
