# Design System — Lot 1 (Fondation + primitives) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** Unifier les tokens (Registre = source unique, shadcn ré-aliasé dessus) et créer les primitives partagées manquantes (`Button`, `Input`, `Select`, `Table`, `Card`, `Tabs`) + une couche de thème Recharts, sans redesigner aucun écran ni ajouter de dépendance.

**Architecture :** Les tokens shadcn de `src/index.css` (`:root`) sont ré-aliasés vers les tokens warm-neutrals du Registre via `var()`, ce qui fait suivre le dark automatiquement (une seule exception : `--primary`/`--accent` gardent leurs HSL brutes navy/gold et reçoivent une valeur `.dark` dédiée). Les primitives sont du **natif stylé** (aucun Radix/Headless) construites avec `cva` (déjà installé, `^0.7.1`) + `cn()` depuis `@/lib/utils`, `forwardRef`, focus visible, radius 4px façon `Panel`, zéro texte en dur. Le thème charts lit les CSS vars et fournit une palette de séries dédiée validée **par script** (le validateur dataviz est copié dans le repo pour être versionné et exécutable hors session).

**Tech Stack :** React 19.2 · Vite · TypeScript · Tailwind 3.4 (`darkMode: class`) · vitest 4.1 + @testing-library/react (jsdom) · Recharts ^3.8 · cva ^0.7.1 · clsx + tailwind-merge · react-i18next.

## Global Constraints

- **Répondre en français** ; toute copie visible passe par i18n (`react-i18next`) — **aucun texte en dur** dans les primitives (les libellés viennent des consommateurs).
- **Devise = XAF** (jamais XOF) partout où une devise apparaît.
- **Aucune nouvelle dépendance** (ni Radix, ni Headless UI, ni lib de charts). Uniquement ce qui est déjà installé : `cva ^0.7.1`, `clsx`, `tailwind-merge`, `recharts ^3.8.1`.
- **Idiome maison** : natif stylé, `cn()` depuis `@/lib/utils`, `forwardRef` sur tout composant de formulaire, focus visible (ring `--ring`), radius 4px (`rounded-[4px]`), montants monétaires en classe `.num`.
- **Ne PAS toucher** les classes `oct-*` de `tailwind.config.js` (`bg-oct-navy`, `text-oct-gold`, …), ni `--primary`/`--accent`/`--primary-foreground`/`--accent-foreground` (identité de marque).
- **Emplacement primitives** : `frontend/src/components/ui/`. **Emplacement tests** : `frontend/src/test/components/` (convention existante — les tests ne sont PAS à côté des composants). Alias `@` = `frontend/src`.
- **Gate vert AVANT chaque commit** : `npx vitest run` 100 % + `npx tsc --noEmit` 0 erreur (dans `frontend/`). Une tâche n'est « done » qu'avec **0 échec** — pas d'excuse « pré-existant ».
- **Un commit par tâche**, message `type(scope): description`.
- Backend inchangé (lot front-only) — ne rien toucher côté Java.
- **Shell** : PowerShell avec `Set-Location "C:\Users\Dany\Documents\FINAL PROJECT\invoice-system\frontend"`. Les commandes de test/tsc se lancent depuis `frontend/`.

---

## Structure des fichiers

**Modifiés :**
- `frontend/src/index.css` — ré-aliasage shadcn→Registre dans `:root` + valeurs `.dark` pour `--primary`/`--accent` (T1).

**Créés (primitives) :**
- `frontend/src/components/ui/Button.tsx` (T2)
- `frontend/src/components/ui/Input.tsx` (T3)
- `frontend/src/components/ui/Select.tsx` (T3)
- `frontend/src/components/ui/Table.tsx` (T4 — sous-composants `Table/THead/TBody/TR/TH/TD`)
- `frontend/src/components/ui/Card.tsx` (T5 — `Card/CardHeader/CardTitle/CardContent/CardFooter` + ré-export alias `Panel`)
- `frontend/src/components/ui/Tabs.tsx` (T5 — `Tabs/TabList/Tab/TabPanel`, maison, a11y clavier)

**Créés (charts) :**
- `frontend/scripts/dataviz/validate_palette.js` (T6 — validateur dataviz copié dans le repo)
- `frontend/src/lib/chart-theme.ts` (T6 — lit les CSS vars + palette de séries)
- `frontend/src/components/ui/ChartTooltip.tsx` (T6 — tooltip Recharts thématisée)

**Tests créés :**
- `frontend/src/test/components/Button.test.tsx` (T2)
- `frontend/src/test/components/Input.test.tsx` (T3)
- `frontend/src/test/components/Select.test.tsx` (T3)
- `frontend/src/test/components/Table.test.tsx` (T4)
- `frontend/src/test/components/Card.test.tsx` (T5)
- `frontend/src/test/components/Tabs.test.tsx` (T5)
- `frontend/src/test/lib/chart-theme.test.ts` (T6)
- `frontend/src/test/components/ChartTooltip.test.tsx` (T6)

**Rappel HORS périmètre (ne rien faire dessus dans ce lot) :** aucune page/dashboard redesignée ; `DashboardPage.tsx` et `VolumeTrendSection.tsx` gardent leurs hex codées en dur et leur dual-axis — leur refonte est un lot ultérieur ; aucun changement de fonte ; pas de `Dialog` (existe déjà).

---

## Rappels tokens (valeurs de référence, déjà dans `index.css`)

Tokens Registre disponibles comme utilitaires Tailwind (via `tailwind.config.js`) : `bg-ground surface hairline hairline-strong`, `text-ink ink-soft ink-faint`, sémantiques `pos warn hot crit info` (+ `-bg`), `gold-deep`. Tokens shadcn : `bg-background card popover primary secondary muted accent destructive`, `border-border/input`, `ring`. Classe `.num` = tabular-nums pour les montants.

Surfaces pour le validateur de palette (T6) :
- **light** : surface graphique = `--surface` = `#FFFFFF` (les cartes/panels sont blancs en light).
- **dark** : surface graphique = `--surface` dark = `#171B22`.

---

### Task 1 : Unification des tokens (`index.css`)

**Files:**
- Modify: `frontend/src/index.css:15-41` (bloc `:root` shadcn) et `frontend/src/index.css:134-155` (bloc `.dark`)

**Interfaces:**
- Consumes: tokens Registre déjà définis dans `:root` (`--ground`, `--surface`, `--hairline`, `--ink`, `--ink-soft`, `--crit`, `--oct-navy`) et leurs overrides `.dark`.
- Produces: variables shadcn (`--background`, `--foreground`, `--card`, `--card-foreground`, `--popover`, `--popover-foreground`, `--border`, `--input`, `--muted`, `--muted-foreground`, `--secondary`, `--secondary-foreground`, `--ring`, `--destructive`) désormais résolues vers le warm Registre et suivant le `.dark` sans duplication ; `--primary`/`--accent` conservent l'identité avec une valeur `.dark` dédiée.

> Détail CSS clé : écrire `--background: var(--ground);` dans `:root` fait que, sous `.dark`, `--background` reprend automatiquement la valeur `.dark` de `--ground` (la `var()` est résolue à l'usage selon la cascade). Donc **on ne duplique PAS** ces alias dans `.dark`. Seuls `--primary`/`--accent` (HSL brutes, ne pointant vers aucun token Registre) ont besoin d'une valeur `.dark`.

- [ ] **Step 1 : Ré-aliaser les neutres + `--destructive` dans `:root`**

Dans `frontend/src/index.css`, remplacer les lignes 15-41 (du bloc `--background` jusqu'à `--ring` inclus) par :

```css
    /* shadcn tokens ré-aliasés vers le Registre warm (Lot 1 / design-system).
     * Les var() sont résolues à l'usage → suivent .dark automatiquement, pas de
     * duplication sous .dark. --primary/--accent gardent leurs HSL brutes navy/gold
     * (identité de marque) et reçoivent une valeur .dark dédiée plus bas. */
    --background: var(--ground);
    --foreground: var(--ink);

    --card: var(--surface);
    --card-foreground: var(--ink);

    --popover: var(--surface);
    --popover-foreground: var(--ink);

    --primary: 213 64% 16%;
    --primary-foreground: 42 53% 80%;

    --secondary: var(--hairline);
    --secondary-foreground: var(--ink);

    --muted: var(--hairline);
    --muted-foreground: var(--ink-soft);

    --accent: 42 53% 54%;
    --accent-foreground: 213 64% 10%;

    --destructive: var(--crit);
    --destructive-foreground: 0 0% 98%;

    --border: var(--hairline);
    --input: var(--hairline);
    --ring: var(--oct-navy);
```

> Note : `--card`/`--popover`/`--background`/etc. sont des triplets HSL bruts consommés via `hsl(var(--card))` dans Tailwind ; les tokens Registre (`--ground`, `--surface`, `--hairline`, `--ink`, `--crit`) sont eux aussi des triplets HSL bruts (voir lignes 82-99), donc `var(--ground)` fournit un triplet valide — l'alias est correct. `--oct-navy` (ligne 8) est également un triplet HSL brut.

- [ ] **Step 2 : Ajouter les valeurs `.dark` de `--primary`/`--accent`**

Dans le bloc `.dark` (se termine actuellement ligne 155 par `--gold-deep: 43 62% 61%;`), ajouter juste avant la `}` fermante du `.dark` :

```css
    /* Identité en dark : navy s'éclaircit (sinon illisible sur fond sombre),
     * gold rehaussé pour ne pas paraître boueux (aligné sur --gold-deep dark). */
    --primary: 213 45% 42%;
    --primary-foreground: 43 13% 90%;
    --accent: 43 62% 61%;
    --accent-foreground: 218 18% 9%;
    --ring: 213 45% 55%;
```

> `--ring` en `:root` pointe vers `var(--oct-navy)` (16% de clarté) — trop sombre sur fond dark, d'où un `--ring` dark éclairci dédié ici.

- [ ] **Step 3 : Vérifier tsc (aucune régression build)**

Run (depuis `frontend/`) : `npx tsc --noEmit`
Expected : 0 erreur (le CSS ne casse pas le typage ; on vérifie qu'aucun import n'a été affecté).

- [ ] **Step 4 : Vérifier vitest (aucune régression)**

Run (depuis `frontend/`) : `npx vitest run`
Expected : 100 % vert, même total qu'avant (baseline 140/140). En particulier `Panel.test.tsx` et `StatusBadge.test.tsx` restent verts (ils testent des classes, pas des valeurs calculées).

- [ ] **Step 5 : Vérification rendu light + dark (obligatoire, critère d'acceptation)**

Lancer le frontend et inspecter visuellement 4 écrans témoins en **light ET dark** : Dashboard, Archive, une page formulaire (ex. création facture), une page tableau (ex. liste factures). Vérifier : fond warm partout, **aucun blanc-sur-blanc**, contrastes tenus, sidebar navy intacte (`.oct-nav-active` inchangé), boutons `bg-oct-navy`/`text-oct-gold` inchangés.

- Démarrer : depuis `frontend/`, `npm run dev` (port 3000). Basculer le thème via le toggle de l'app (classe `.dark` sur `<html>`/`<body>`).
- Si le skill `run` ou l'outil Playwright est disponible, capturer une capture par écran/thème pour comparaison. Sinon, inspection manuelle.
- **Ne PAS committer** tant qu'une régression visuelle subsiste.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/index.css
git commit -m "refactor(tokens): ré-aliase shadcn vers le Registre warm + valeurs dark identité"
```

---

### Task 2 : Primitive `Button`

**Files:**
- Create: `frontend/src/components/ui/Button.tsx`
- Test: `frontend/src/test/components/Button.test.tsx`

**Interfaces:**
- Consumes: `cn` depuis `@/lib/utils` ; `cva`, `type VariantProps` depuis `class-variance-authority`.
- Produces:
  - `buttonVariants({ variant, size })` — helper `cva` réutilisable par d'autres composants.
  - `Button` — `React.forwardRef<HTMLButtonElement, ButtonProps>`.
  - `interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { loading?: boolean }`.
  - Variants : `primary` (défaut) · `secondary` · `ghost` · `destructive` · `gold`. Sizes : `sm` · `md` (défaut) · `lg` · `icon`.

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `frontend/src/test/components/Button.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Button } from '@/components/ui/Button'

describe('Button', () => {
  it('rend son contenu (libellé fourni par le consommateur, pas de texte en dur)', () => {
    render(<Button>Enregistrer</Button>)
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeInTheDocument()
  })

  it('applique la variante primary par défaut (navy)', () => {
    render(<Button>x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-oct-navy/)
  })

  it('applique la variante gold (CTA premium, gold-deep en fond)', () => {
    render(<Button variant="gold">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-gold-deep/)
  })

  it('applique la variante destructive (crit)', () => {
    render(<Button variant="destructive">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-crit/)
  })

  it('expose un ring de focus visible', () => {
    render(<Button>x</Button>)
    expect(screen.getByRole('button').className).toMatch(/focus-visible:ring/)
  })

  it('state loading : disabled + aria-busy + spinner', () => {
    render(<Button loading>x</Button>)
    const btn = screen.getByRole('button')
    expect(btn).toBeDisabled()
    expect(btn).toHaveAttribute('aria-busy', 'true')
  })

  it('forwarde la ref vers le <button>', () => {
    const ref = createRef<HTMLButtonElement>()
    render(<Button ref={ref}>x</Button>)
    expect(ref.current?.tagName).toBe('BUTTON')
  })

  it('fusionne une className extra', () => {
    render(<Button className="w-full">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/w-full/)
  })
})
```

- [ ] **Step 2 : Lancer le test pour vérifier l'échec**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Button.test.tsx`
Expected : FAIL (module `@/components/ui/Button` introuvable).

- [ ] **Step 3 : Écrire l'implémentation minimale**

Créer `frontend/src/components/ui/Button.tsx` :

```tsx
import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Bouton partagé (Lot 1 / design-system). Natif stylé, zéro dépendance nouvelle.
 * `primary` = navy (structure) ; `gold` = CTA premium rare, seul endroit où le
 * gold vit en fond (--gold-deep). Aucun texte en dur : le libellé vient du
 * consommateur (i18n côté appelant). focus-visible ring navy (--ring).
 */
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-[4px] font-medium ' +
    'transition-colors focus-visible:outline-none focus-visible:ring-2 ' +
    'focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background ' +
    'disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-oct-navy text-white hover:bg-oct-navy-light',
        secondary: 'bg-surface text-ink border border-hairline hover:bg-hairline/40',
        ghost: 'bg-transparent text-ink hover:bg-hairline/40',
        destructive: 'bg-crit text-white hover:bg-crit/90',
        gold: 'bg-gold-deep text-oct-navy-dark hover:bg-gold-deep/90',
      },
      size: {
        sm: 'h-8 px-3 text-xs',
        md: 'h-10 px-4 text-sm',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
)
Button.displayName = 'Button'

export { buttonVariants }
```

> `lucide-react` est déjà une dépendance du projet (utilisée dans `VolumeTrendSection.tsx` et ailleurs) — ce n'est pas une nouvelle dépendance.

- [ ] **Step 4 : Lancer le test pour vérifier le succès**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Button.test.tsx`
Expected : PASS (8 tests).

- [ ] **Step 5 : Gate complet (tsc + vitest global)**

Run (depuis `frontend/`) : `npx tsc --noEmit` → 0 erreur. Puis `npx vitest run` → 100 % vert.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/components/ui/Button.tsx frontend/src/test/components/Button.test.tsx
git commit -m "feat(ui): primitive Button (cva, 5 variants, loading, forwardRef)"
```

---

### Task 3 : Primitives `Input` + `Select`

**Files:**
- Create: `frontend/src/components/ui/Input.tsx`, `frontend/src/components/ui/Select.tsx`
- Test: `frontend/src/test/components/Input.test.tsx`, `frontend/src/test/components/Select.test.tsx`

**Interfaces:**
- Consumes: `cn` depuis `@/lib/utils`.
- Produces:
  - `Input` — `React.forwardRef<HTMLInputElement, InputProps>` ; `interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> { error?: boolean }`.
  - `Select` — `React.forwardRef<HTMLSelectElement, SelectProps>` ; `interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> { error?: boolean }` ; le consommateur passe les `<option>` en `children`.

- [ ] **Step 1 : Écrire les tests qui échouent**

Créer `frontend/src/test/components/Input.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Input } from '@/components/ui/Input'

describe('Input', () => {
  it('rend un champ texte accessible via son placeholder', () => {
    render(<Input placeholder="Référence" />)
    expect(screen.getByPlaceholderText('Référence')).toBeInTheDocument()
  })

  it('grammaire visuelle de base : surface, hairline, ring', () => {
    render(<Input aria-label="ref" />)
    const el = screen.getByLabelText('ref')
    expect(el.className).toMatch(/bg-surface/)
    expect(el.className).toMatch(/border-hairline/)
    expect(el.className).toMatch(/focus-visible:ring/)
  })

  it('state erreur : bordure crit + aria-invalid', () => {
    render(<Input aria-label="ref" error />)
    const el = screen.getByLabelText('ref')
    expect(el).toHaveAttribute('aria-invalid', 'true')
    expect(el.className).toMatch(/border-crit/)
  })

  it('forwarde la ref', () => {
    const ref = createRef<HTMLInputElement>()
    render(<Input ref={ref} aria-label="ref" />)
    expect(ref.current?.tagName).toBe('INPUT')
  })
})
```

Créer `frontend/src/test/components/Select.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Select } from '@/components/ui/Select'

describe('Select', () => {
  it('rend un <select> natif avec les options fournies', () => {
    render(
      <Select aria-label="statut">
        <option value="a">A</option>
        <option value="b">B</option>
      </Select>
    )
    const el = screen.getByLabelText('statut')
    expect(el.tagName).toBe('SELECT')
    expect(screen.getByRole('option', { name: 'A' })).toBeInTheDocument()
  })

  it('grammaire visuelle alignée sur Input : surface, hairline, ring', () => {
    render(<Select aria-label="s"><option>x</option></Select>)
    const el = screen.getByLabelText('s')
    expect(el.className).toMatch(/bg-surface/)
    expect(el.className).toMatch(/border-hairline/)
    expect(el.className).toMatch(/focus-visible:ring/)
  })

  it('state erreur : bordure crit + aria-invalid', () => {
    render(<Select aria-label="s" error><option>x</option></Select>)
    const el = screen.getByLabelText('s')
    expect(el).toHaveAttribute('aria-invalid', 'true')
    expect(el.className).toMatch(/border-crit/)
  })

  it('forwarde la ref', () => {
    const ref = createRef<HTMLSelectElement>()
    render(<Select ref={ref} aria-label="s"><option>x</option></Select>)
    expect(ref.current?.tagName).toBe('SELECT')
  })
})
```

- [ ] **Step 2 : Lancer les tests pour vérifier l'échec**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Input.test.tsx src/test/components/Select.test.tsx`
Expected : FAIL (modules introuvables).

- [ ] **Step 3 : Écrire les implémentations minimales**

Créer `frontend/src/components/ui/Input.tsx` :

```tsx
import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Champ de saisie partagé (Lot 1 / design-system). Natif stylé. Grammaire :
 * fond surface, bordure hairline, texte ink, placeholder ink-faint, ring navy.
 * `error` → bordure/ring crit + aria-invalid. Aucun texte en dur.
 */
export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, error, ...props }, ref) => (
    <input
      ref={ref}
      aria-invalid={error || undefined}
      className={cn(
        'flex h-10 w-full rounded-[4px] border bg-surface px-3 py-2 text-sm text-ink',
        'placeholder:text-ink-faint transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        'disabled:cursor-not-allowed disabled:opacity-50',
        error
          ? 'border-crit focus-visible:ring-crit'
          : 'border-hairline focus-visible:ring-ring',
        className
      )}
      {...props}
    />
  )
)
Input.displayName = 'Input'
```

Créer `frontend/src/components/ui/Select.tsx` :

```tsx
import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * <select> natif stylé (Lot 1 / design-system) — pas de dropdown custom, pas de
 * Radix. Même grammaire visuelle qu'Input ; chevron via background SVG inline.
 * Les <option> sont fournies en children par le consommateur. Aucun texte en dur.
 */
const CHEVRON =
  "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236B6456' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>\")"

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: boolean
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, children, style, ...props }, ref) => (
    <select
      ref={ref}
      aria-invalid={error || undefined}
      style={{
        backgroundImage: CHEVRON,
        backgroundRepeat: 'no-repeat',
        backgroundPosition: 'right 0.75rem center',
        ...style,
      }}
      className={cn(
        'flex h-10 w-full appearance-none rounded-[4px] border bg-surface pl-3 pr-9 py-2 text-sm text-ink',
        'transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        'disabled:cursor-not-allowed disabled:opacity-50',
        error
          ? 'border-crit focus-visible:ring-crit'
          : 'border-hairline focus-visible:ring-ring',
        className
      )}
      {...props}
    >
      {children}
    </select>
  )
)
Select.displayName = 'Select'
```

- [ ] **Step 4 : Lancer les tests pour vérifier le succès**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Input.test.tsx src/test/components/Select.test.tsx`
Expected : PASS (4 + 4 tests).

- [ ] **Step 5 : Gate complet**

Run (depuis `frontend/`) : `npx tsc --noEmit` → 0 erreur. Puis `npx vitest run` → 100 % vert.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/components/ui/Input.tsx frontend/src/components/ui/Select.tsx frontend/src/test/components/Input.test.tsx frontend/src/test/components/Select.test.tsx
git commit -m "feat(ui): primitives Input + Select natifs stylés (forwardRef, error, focus)"
```

---

### Task 4 : Primitive `Table` (sous-composants)

**Files:**
- Create: `frontend/src/components/ui/Table.tsx`
- Test: `frontend/src/test/components/Table.test.tsx`

**Interfaces:**
- Consumes: `cn` depuis `@/lib/utils`.
- Produces (tous `forwardRef`, aucune logique métier) :
  - `Table` (`HTMLTableElement`) — wrappe `<table>` dans un conteneur `overflow-x-auto`.
  - `THead` (`HTMLTableSectionElement`) · `TBody` (`HTMLTableSectionElement`).
  - `TR` (`HTMLTableRowElement`) · `TH` (`HTMLTableCellElement`) · `TD` (`HTMLTableCellElement`).

- [ ] **Step 1 : Écrire le test qui échoue**

Créer `frontend/src/test/components/Table.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Table, THead, TBody, TR, TH, TD } from '@/components/ui/Table'

describe('Table', () => {
  it('rend une table sémantique avec en-tête et lignes', () => {
    render(
      <Table>
        <THead>
          <TR><TH>Référence</TH><TH>Montant</TH></TR>
        </THead>
        <TBody>
          <TR><TD>F-001</TD><TD>1 000 XAF</TD></TR>
        </TBody>
      </Table>
    )
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Référence' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'F-001' })).toBeInTheDocument()
  })

  it('enveloppe la table dans un conteneur scrollable horizontalement', () => {
    const { container } = render(<Table><TBody><TR><TD>x</TD></TR></TBody></Table>)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/overflow-x-auto/)
  })

  it('en-tête : encre douce + uppercase ; lignes : séparateur hairline', () => {
    render(
      <Table>
        <THead><TR><TH>Statut</TH></TR></THead>
        <TBody><TR data-testid="row"><TD>x</TD></TR></TBody>
      </Table>
    )
    expect(screen.getByRole('columnheader').className).toMatch(/text-ink-soft/)
    expect(screen.getByRole('columnheader').className).toMatch(/uppercase/)
    expect(screen.getByTestId('row').className).toMatch(/border-hairline/)
  })

  it('fusionne une className extra sur une cellule (ex. .num pour montants)', () => {
    render(<Table><TBody><TR><TD className="num">1 000</TD></TR></TBody></Table>)
    expect(screen.getByRole('cell').className).toMatch(/num/)
  })
})
```

- [ ] **Step 2 : Lancer le test pour vérifier l'échec**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Table.test.tsx`
Expected : FAIL (module introuvable).

- [ ] **Step 3 : Écrire l'implémentation minimale**

Créer `frontend/src/components/ui/Table.tsx` :

```tsx
import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Set de sous-composants table partagés (Lot 1 / design-system). Zéro logique
 * métier. En-tête ink-soft uppercase fin ; lignes séparées border-hairline,
 * hover hairline/40. Montants monétaires : ajouter className="num" sur le TD.
 * Le conteneur overflow-x-auto évite tout scroll horizontal de page.
 */
export const Table = React.forwardRef<
  HTMLTableElement,
  React.TableHTMLAttributes<HTMLTableElement>
>(({ className, ...props }, ref) => (
  <div className="w-full overflow-x-auto">
    <table ref={ref} className={cn('w-full border-collapse text-sm', className)} {...props} />
  </div>
))
Table.displayName = 'Table'

export const THead = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <thead ref={ref} className={cn('border-b border-hairline', className)} {...props} />
))
THead.displayName = 'THead'

export const TBody = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tbody ref={ref} className={className} {...props} />
))
TBody.displayName = 'TBody'

export const TR = React.forwardRef<
  HTMLTableRowElement,
  React.HTMLAttributes<HTMLTableRowElement>
>(({ className, ...props }, ref) => (
  <tr
    ref={ref}
    className={cn('border-b border-hairline transition-colors hover:bg-hairline/40', className)}
    {...props}
  />
))
TR.displayName = 'TR'

export const TH = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      'px-4 py-2.5 text-left align-middle text-xs font-medium uppercase tracking-wide text-ink-soft',
      className
    )}
    {...props}
  />
))
TH.displayName = 'TH'

export const TD = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <td ref={ref} className={cn('px-4 py-3 align-middle text-ink', className)} {...props} />
))
TD.displayName = 'TD'
```

- [ ] **Step 4 : Lancer le test pour vérifier le succès**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Table.test.tsx`
Expected : PASS (4 tests).

- [ ] **Step 5 : Gate complet**

Run (depuis `frontend/`) : `npx tsc --noEmit` → 0 erreur. Puis `npx vitest run` → 100 % vert.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/components/ui/Table.tsx frontend/src/test/components/Table.test.tsx
git commit -m "feat(ui): primitive Table (sous-composants, hairline, overflow-x-auto)"
```

---

### Task 5 : Primitives `Card` (+ alias `Panel`) et `Tabs`

**Files:**
- Create: `frontend/src/components/ui/Card.tsx`, `frontend/src/components/ui/Tabs.tsx`
- Test: `frontend/src/test/components/Card.test.tsx`, `frontend/src/test/components/Tabs.test.tsx`

**Interfaces:**
- Consumes: `cn` depuis `@/lib/utils`.
- Produces:
  - `Card` + `CardHeader` + `CardTitle` + `CardContent` + `CardFooter` (tous `forwardRef<HTMLDivElement, ...>`, `CardTitle` sur `HTMLHeadingElement`). Même grammaire que `Panel` (`bg-surface rounded-[4px] border border-hairline shadow-sm`).
  - `Tabs` : composant contrôlé/non-contrôlé maison via contexte React.
    - `interface TabsProps { value?: string; defaultValue?: string; onValueChange?: (v: string) => void; children: React.ReactNode; className?: string }`.
    - `TabList` (`role="tablist"`, gère les flèches ←/→, Home/End).
    - `Tab` (`role="tab"`, `aria-selected`, `value: string`).
    - `TabPanel` (`role="tabpanel"`, `value: string`, rendu seulement si actif).

> **Note rétro-compat `Panel`** : le composant `Panel` existant (`frontend/src/components/ui/Panel.tsx`) reste en place et n'est PAS modifié — la spec le veut comme alias rétro-compatible. `Card` est le nouveau composant riche ; on n'a pas besoin de faire de `Panel` un ré-export de `Card` (leur API diffère : `Panel` prend `title?`, `Card` prend des sous-composants). On garde les deux ; les usages existants de `Panel` ne cassent pas.

- [ ] **Step 1 : Écrire les tests qui échouent**

Créer `frontend/src/test/components/Card.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '@/components/ui/Card'

describe('Card', () => {
  it('rend la grammaire visuelle Panel (surface, radius 4px, hairline, shadow)', () => {
    const { container } = render(<Card>x</Card>)
    const root = container.firstElementChild as HTMLElement
    expect(root.className).toMatch(/bg-surface/)
    expect(root.className).toMatch(/rounded-\[4px\]/)
    expect(root.className).toMatch(/border-hairline/)
    expect(root.className).toMatch(/shadow-sm/)
  })

  it('compose header/title/content/footer', () => {
    render(
      <Card>
        <CardHeader><CardTitle>Résumé</CardTitle></CardHeader>
        <CardContent>Corps</CardContent>
        <CardFooter>Pied</CardFooter>
      </Card>
    )
    expect(screen.getByRole('heading', { name: 'Résumé' })).toBeInTheDocument()
    expect(screen.getByText('Corps')).toBeInTheDocument()
    expect(screen.getByText('Pied')).toBeInTheDocument()
  })

  it('fusionne une className extra', () => {
    const { container } = render(<Card className="mt-6">x</Card>)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/mt-6/)
  })
})
```

Créer `frontend/src/test/components/Tabs.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Tabs, TabList, Tab, TabPanel } from '@/components/ui/Tabs'

function Fixture() {
  return (
    <Tabs defaultValue="a">
      <TabList>
        <Tab value="a">Onglet A</Tab>
        <Tab value="b">Onglet B</Tab>
      </TabList>
      <TabPanel value="a">Panneau A</TabPanel>
      <TabPanel value="b">Panneau B</TabPanel>
    </Tabs>
  )
}

describe('Tabs', () => {
  it('expose les rôles ARIA tablist/tab/tabpanel', () => {
    render(<Fixture />)
    expect(screen.getByRole('tablist')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(2)
  })

  it("affiche le panneau de l'onglet actif par défaut, cache l'autre", () => {
    render(<Fixture />)
    expect(screen.getByText('Panneau A')).toBeInTheDocument()
    expect(screen.queryByText('Panneau B')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Onglet A' })).toHaveAttribute('aria-selected', 'true')
  })

  it('change de panneau au clic', async () => {
    const user = userEvent.setup()
    render(<Fixture />)
    await user.click(screen.getByRole('tab', { name: 'Onglet B' }))
    expect(screen.getByText('Panneau B')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Onglet B' })).toHaveAttribute('aria-selected', 'true')
  })

  it('navigation clavier : flèche droite active l\'onglet suivant', async () => {
    const user = userEvent.setup()
    render(<Fixture />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Onglet B' })).toHaveAttribute('aria-selected', 'true')
  })
})
```

> `@testing-library/user-event` est déjà utilisé dans la suite existante (présent en devDependency). Vérifier l'import : si un test existant l'importe déjà, la dépendance est confirmée disponible.

- [ ] **Step 2 : Lancer les tests pour vérifier l'échec**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Card.test.tsx src/test/components/Tabs.test.tsx`
Expected : FAIL (modules introuvables).

- [ ] **Step 3 : Écrire les implémentations minimales**

Créer `frontend/src/components/ui/Card.tsx` :

```tsx
import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Card partagée (Lot 1 / design-system) — généralise Panel avec des
 * sous-composants. Même grammaire visuelle (surface, radius 4px, hairline,
 * shadow near-flat). Panel reste disponible comme alias rétro-compatible
 * (composant distinct, API title?). Aucun texte en dur.
 */
export const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn('bg-surface rounded-[4px] border border-hairline shadow-sm', className)}
      {...props}
    />
  )
)
Card.displayName = 'Card'

export const CardHeader = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('px-5 py-4 border-b border-hairline', className)} {...props} />
  )
)
CardHeader.displayName = 'CardHeader'

export const CardTitle = React.forwardRef<
  HTMLHeadingElement,
  React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h2 ref={ref} className={cn('font-semibold text-ink', className)} {...props} />
))
CardTitle.displayName = 'CardTitle'

export const CardContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('p-5', className)} {...props} />
  )
)
CardContent.displayName = 'CardContent'

export const CardFooter = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('px-5 py-4 border-t border-hairline', className)} {...props} />
  )
)
CardFooter.displayName = 'CardFooter'
```

Créer `frontend/src/components/ui/Tabs.tsx` :

```tsx
import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Tabs maison (Lot 1 / design-system) — pas de Radix. role=tablist/tab/tabpanel,
 * aria-selected, navigation clavier (←/→, Home/End). Contrôlé (value) ou
 * non-contrôlé (defaultValue). Onglet actif = sous-ligne navy. Aucun texte en dur.
 */
interface TabsContextValue {
  value: string
  setValue: (v: string) => void
  register: (v: string) => void
  order: React.MutableRefObject<string[]>
}
const TabsContext = React.createContext<TabsContextValue | null>(null)
function useTabs() {
  const ctx = React.useContext(TabsContext)
  if (!ctx) throw new Error('Les sous-composants Tabs doivent être utilisés dans <Tabs>')
  return ctx
}

export interface TabsProps {
  value?: string
  defaultValue?: string
  onValueChange?: (v: string) => void
  children: React.ReactNode
  className?: string
}

export function Tabs({ value, defaultValue, onValueChange, children, className }: TabsProps) {
  const [internal, setInternal] = React.useState(defaultValue ?? '')
  const current = value ?? internal
  const order = React.useRef<string[]>([])

  const setValue = React.useCallback(
    (v: string) => {
      if (value === undefined) setInternal(v)
      onValueChange?.(v)
    },
    [value, onValueChange]
  )
  const register = React.useCallback((v: string) => {
    if (!order.current.includes(v)) order.current.push(v)
  }, [])

  return (
    <TabsContext.Provider value={{ value: current, setValue, register, order }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

export function TabList({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const { value, setValue, order } = useTabs()
  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const list = order.current
    const i = list.indexOf(value)
    if (i < 0) return
    let next: number | null = null
    if (e.key === 'ArrowRight') next = (i + 1) % list.length
    else if (e.key === 'ArrowLeft') next = (i - 1 + list.length) % list.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = list.length - 1
    if (next !== null) {
      e.preventDefault()
      setValue(list[next])
    }
  }
  return (
    <div
      role="tablist"
      onKeyDown={onKeyDown}
      className={cn('flex items-center gap-1 border-b border-hairline', className)}
      {...props}
    />
  )
}

export interface TabProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  value: string
}
export function Tab({ value: tabValue, className, ...props }: TabProps) {
  const { value, setValue, register } = useTabs()
  React.useEffect(() => register(tabValue), [register, tabValue])
  const active = value === tabValue
  return (
    <button
      role="tab"
      type="button"
      aria-selected={active}
      tabIndex={active ? 0 : -1}
      onClick={() => setValue(tabValue)}
      className={cn(
        'px-4 py-2.5 -mb-px border-b-2 text-sm font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1',
        active
          ? 'border-oct-navy text-ink'
          : 'border-transparent text-ink-soft hover:text-ink',
        className
      )}
      {...props}
    />
  )
}

export interface TabPanelProps extends React.HTMLAttributes<HTMLDivElement> {
  value: string
}
export function TabPanel({ value: panelValue, className, children, ...props }: TabPanelProps) {
  const { value } = useTabs()
  if (value !== panelValue) return null
  return (
    <div role="tabpanel" className={className} {...props}>
      {children}
    </div>
  )
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier le succès**

Run (depuis `frontend/`) : `npx vitest run src/test/components/Card.test.tsx src/test/components/Tabs.test.tsx`
Expected : PASS (3 + 4 tests).

- [ ] **Step 5 : Gate complet**

Run (depuis `frontend/`) : `npx tsc --noEmit` → 0 erreur. Puis `npx vitest run` → 100 % vert.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/components/ui/Card.tsx frontend/src/components/ui/Tabs.tsx frontend/src/test/components/Card.test.tsx frontend/src/test/components/Tabs.test.tsx
git commit -m "feat(ui): primitives Card (sous-composants) + Tabs maison (a11y clavier)"
```

---

### Task 6 : Thème charts — validateur versionné + `chart-theme.ts` + `ChartTooltip`

**Files:**
- Create: `frontend/scripts/dataviz/validate_palette.js` (copie du validateur dataviz, versionnée)
- Create: `frontend/src/lib/chart-theme.ts`
- Create: `frontend/src/components/ui/ChartTooltip.tsx`
- Test: `frontend/src/test/lib/chart-theme.test.ts`, `frontend/src/test/components/ChartTooltip.test.tsx`

**Interfaces:**
- Consumes: `cn` depuis `@/lib/utils`.
- Produces:
  - `chart-theme.ts` :
    - `SERIES_PALETTE_LIGHT: string[]` et `SERIES_PALETTE_DARK: string[]` — palette catégorielle d'ordre fixe, distincte des couleurs de statut.
    - `getSeriesColor(index: number, dark?: boolean): string` — retourne la couleur de série à l'index (clamp/fold au-delà de la longueur, jamais de hue générée).
    - `chartAxisProps` / `chartGridProps` — objets de props par défaut pour `XAxis`/`YAxis`/`CartesianGrid` (couleurs via CSS vars `hsl(var(--ink-faint))`, `hsl(var(--hairline))`).
  - `ChartTooltip` — composant compatible `content={<ChartTooltip/>}` de Recharts. Signature : `interface ChartTooltipProps { active?: boolean; payload?: Array<{ name?: string; value?: number | string; color?: string }>; label?: string | number; valueFormatter?: (v: number | string) => string }`.

- [ ] **Step 1 : Copier le validateur dans le repo (artefact versionné)**

Le validateur dataviz vit dans un bundle de skill temporaire (chemin volatile entre sessions). Le copier dans le repo pour qu'il soit versionné et exécutable indépendamment.

- Localiser le fichier source : chercher `validate_palette.js` sous le répertoire de base du skill `dataviz` (affiché quand le skill est chargé — typiquement `.../bundled-skills/<ver>/<hash>/dataviz/scripts/validate_palette.js`).
- Copier son contenu **verbatim** dans `frontend/scripts/dataviz/validate_palette.js` (créer le dossier). Ne rien modifier au code du validateur.

> Si le chemin exact n'est pas connu au moment de l'exécution : charger le skill `dataviz` (via l'outil Skill) affiche « Base directory for this skill: <path> » ; le validateur est à `<path>/scripts/validate_palette.js`.

- [ ] **Step 2 : Choisir la palette de séries candidate et la VALIDER par script (light + dark)**

Palette catégorielle candidate — ordre fixe, **distincte des sémantiques de statut** (`pos #3E7C5A`, `warn #B5852A`, `hot #C4622E`, `crit #A6432E`, `info #2F6690`). Base : navy → gold-deep → info → hues additionnels choisis pour maximiser la séparation CVD. Le validateur va confirmer/infirmer par calcul.

Palette light candidate (8 slots) :
`#2F6690,#9A7E2E,#3E7C5A,#7A4E8C,#C4622E,#4A6FA5,#B0472E,#5E8C6A`

> **Ce ne sont PAS des valeurs figées.** Le validateur peut FAIL certaines (bande de clarté / chroma / CVD adjacent). Si un slot FAIL, l'ajuster (assombrir/éclaircir/re-saturer sur la même hue, ou ré-ordonner) et **re-valider** jusqu'à `ALL CHECKS PASS` (ou au pire CVD dans la bande floor 8–12, légal seulement avec encodage secondaire — libellés directs/texture). Réordonner pour maximiser le ΔE adjacent minimum.

Run (depuis `frontend/`), surface light = `#FFFFFF` :

```bash
node scripts/dataviz/validate_palette.js "#2F6690,#9A7E2E,#3E7C5A,#7A4E8C,#C4622E,#4A6FA5,#B0472E,#5E8C6A" --mode light --surface "#FFFFFF"
```

Expected : `ALL CHECKS PASS` (ou floor band documenté). Corriger les slots FAIL et re-lancer jusqu'à passage. Noter la palette light finale retenue.

- [ ] **Step 3 : Dériver et valider la palette dark (surface dark `#171B22`)**

Pour chaque slot, choisir un step rehaussé (plus clair/saturé) adapté à la bande dark (OKLCH L ≈ 0.48–0.67). Candidat de départ (à ajuster selon le validateur) :
`#5E9BC4,#D9B65E,#6FBE93,#B08AC4,#E0925E,#7E9FD0,#E0806A,#8FBFA0`

Run (depuis `frontend/`) :

```bash
node scripts/dataviz/validate_palette.js "#5E9BC4,#D9B65E,#6FBE93,#B08AC4,#E0925E,#7E9FD0,#E0806A,#8FBFA0" --mode dark --surface "#171B22"
```

Expected : `ALL CHECKS PASS` (ou floor band documenté). Corriger et re-lancer jusqu'à passage. Noter la palette dark finale retenue.

- [ ] **Step 4 : Croiser avec la base ui-ux-pro-max (traçabilité)**

Depuis `C:\Users\Dany\.claude\skills\ui-ux-pro-max`, lancer (Windows : `python`, pas `python3`) :

```bash
python scripts/search.py "categorical series palette financial dashboard" --domain color
```

Confirmer que la direction (bleus/verts institutionnels finance, statut réservé) est cohérente avec les résultats. C'est une vérification de bon sens, pas un blocage — la source de vérité reste le validateur par script.

- [ ] **Step 5 : Écrire les tests qui échouent**

Créer `frontend/src/test/lib/chart-theme.test.ts` :

```ts
import { describe, it, expect } from 'vitest'
import {
  SERIES_PALETTE_LIGHT,
  SERIES_PALETTE_DARK,
  getSeriesColor,
} from '@/lib/chart-theme'

const STATUS = ['#3E7C5A', '#B5852A', '#C4622E', '#A6432E', '#2F6690']

describe('chart-theme palette', () => {
  it('les deux modes ont le même nombre de slots (mêmes entités re-stepées)', () => {
    expect(SERIES_PALETTE_LIGHT.length).toBe(SERIES_PALETTE_DARK.length)
    expect(SERIES_PALETTE_LIGHT.length).toBeGreaterThanOrEqual(6)
  })

  it('la palette de séries light n\'empiète pas sur les couleurs de statut', () => {
    for (const c of SERIES_PALETTE_LIGHT) {
      expect(STATUS).not.toContain(c.toUpperCase())
    }
  })

  it('getSeriesColor suit l\'entité par index (ordre fixe, jamais cyclé en hue générée)', () => {
    expect(getSeriesColor(0)).toBe(SERIES_PALETTE_LIGHT[0])
    expect(getSeriesColor(1)).toBe(SERIES_PALETTE_LIGHT[1])
    expect(getSeriesColor(0, true)).toBe(SERIES_PALETTE_DARK[0])
  })

  it('getSeriesColor replie (fold) au-delà de la longueur au lieu de générer une hue', () => {
    const n = SERIES_PALETTE_LIGHT.length
    // au-delà de n : reste dans la palette (dernier slot ou "Other"), pas de undefined
    expect(SERIES_PALETTE_LIGHT).toContain(getSeriesColor(n + 3))
  })
})
```

Créer `frontend/src/test/components/ChartTooltip.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartTooltip } from '@/components/ui/ChartTooltip'

describe('ChartTooltip', () => {
  it('ne rend rien quand inactif', () => {
    const { container } = render(<ChartTooltip active={false} payload={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('rend le label et les entrées de série quand actif', () => {
    render(
      <ChartTooltip
        active
        label="Janvier"
        payload={[{ name: 'Montant', value: 1000, color: '#2F6690' }]}
      />
    )
    expect(screen.getByText('Janvier')).toBeInTheDocument()
    expect(screen.getByText('Montant')).toBeInTheDocument()
    expect(screen.getByText('1000')).toBeInTheDocument()
  })

  it('applique valueFormatter aux montants', () => {
    render(
      <ChartTooltip
        active
        label="Janvier"
        payload={[{ name: 'Montant', value: 1000, color: '#2F6690' }]}
        valueFormatter={(v) => `${v} XAF`}
      />
    )
    expect(screen.getByText('1000 XAF')).toBeInTheDocument()
  })

  it('grammaire visuelle : surface + hairline', () => {
    const { container } = render(
      <ChartTooltip active label="x" payload={[{ name: 'S', value: 1, color: '#000' }]} />
    )
    expect((container.firstElementChild as HTMLElement).className).toMatch(/bg-surface/)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/border-hairline/)
  })
})
```

- [ ] **Step 6 : Lancer les tests pour vérifier l'échec**

Run (depuis `frontend/`) : `npx vitest run src/test/lib/chart-theme.test.ts src/test/components/ChartTooltip.test.tsx`
Expected : FAIL (modules introuvables).

- [ ] **Step 7 : Écrire `chart-theme.ts`**

Créer `frontend/src/lib/chart-theme.ts` — **remplacer les tableaux ci-dessous par les palettes FINALES validées aux steps 2-3** :

```ts
/**
 * Thème charts (Lot 1 / design-system). Couche de thème au-dessus de Recharts —
 * pas de remplacement de lib. Axes/grille recessifs via CSS vars (suivent .dark).
 * Palette de séries d'ORDRE FIXE, distincte des couleurs de statut
 * (pos/warn/hot/crit/info réservées). Validée par script :
 *   frontend/scripts/dataviz/validate_palette.js (light surface #FFFFFF, dark #171B22).
 * Au-delà de 8 séries : replier sur "Other" / small multiples — jamais une hue générée.
 */

// ⚠️ Remplacer par les palettes exactes retournées PASS par le validateur (steps 2-3).
export const SERIES_PALETTE_LIGHT: string[] = [
  '#2F6690', '#9A7E2E', '#3E7C5A', '#7A4E8C', '#C4622E', '#4A6FA5', '#B0472E', '#5E8C6A',
]
export const SERIES_PALETTE_DARK: string[] = [
  '#5E9BC4', '#D9B65E', '#6FBE93', '#B08AC4', '#E0925E', '#7E9FD0', '#E0806A', '#8FBFA0',
]

/** Couleur de série par index (l'entité, pas le rang). Au-delà de la longueur,
 *  replie sur le dernier slot plutôt que de générer/cycler une hue. */
export function getSeriesColor(index: number, dark = false): string {
  const palette = dark ? SERIES_PALETTE_DARK : SERIES_PALETTE_LIGHT
  return palette[Math.min(index, palette.length - 1)]
}

/** Props recessives pour CartesianGrid — lignes fines hairline, pas de verticales. */
export const chartGridProps = {
  stroke: 'hsl(var(--hairline))',
  strokeDasharray: '3 3',
  vertical: false,
} as const

/** Props d'axe — ticks ink-faint, ligne d'axe hairline, pas de tickLine. */
export const chartAxisProps = {
  tick: { fontSize: 11, fill: 'hsl(var(--ink-faint))' },
  axisLine: { stroke: 'hsl(var(--hairline))' },
  tickLine: false,
} as const
```

- [ ] **Step 8 : Écrire `ChartTooltip.tsx`**

Créer `frontend/src/components/ui/ChartTooltip.tsx` :

```tsx
import { cn } from '@/lib/utils'

/**
 * Tooltip Recharts thématisée (Lot 1 / design-system). Passée via
 * content={<ChartTooltip valueFormatter={...} />}. Surface + hairline + ink ;
 * pastille de série ; montants en .num (via valueFormatter côté appelant, ou
 * la classe .num sur la valeur). Le texte porte des tokens d'encre, jamais la
 * couleur de série (celle-ci n'est que la pastille). Aucun texte en dur.
 */
export interface ChartTooltipProps {
  active?: boolean
  payload?: Array<{ name?: string; value?: number | string; color?: string }>
  label?: string | number
  valueFormatter?: (v: number | string) => string
}

export function ChartTooltip({ active, payload, label, valueFormatter }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) return null
  return (
    <div className={cn('bg-surface border border-hairline rounded-[4px] shadow-sm px-3 py-2 text-xs')}>
      {label !== undefined && <div className="mb-1 font-medium text-ink">{label}</div>}
      <ul className="space-y-0.5">
        {payload.map((entry, i) => (
          <li key={i} className="flex items-center gap-2 text-ink-soft">
            <span
              className="inline-block w-2 h-2 rounded-full shrink-0"
              style={{ backgroundColor: entry.color }}
              aria-hidden="true"
            />
            <span>{entry.name}</span>
            <span className="num ml-auto text-ink">
              {entry.value !== undefined && valueFormatter
                ? valueFormatter(entry.value)
                : entry.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
```

- [ ] **Step 9 : Lancer les tests pour vérifier le succès**

Run (depuis `frontend/`) : `npx vitest run src/test/lib/chart-theme.test.ts src/test/components/ChartTooltip.test.tsx`
Expected : PASS (4 + 4 tests). Ajuster le test `chart-theme` si la palette finale a un nombre de slots différent (garder l'assertion de non-empiètement sur les statuts).

- [ ] **Step 10 : Gate complet**

Run (depuis `frontend/`) : `npx tsc --noEmit` → 0 erreur. Puis `npx vitest run` → 100 % vert.

- [ ] **Step 11 : Rendu témoin (étape « render it and look at it » de dataviz)**

Vérification visuelle légère : brancher un mini-exemple de rendu (temporaire, NON commité — ou une story locale) utilisant `chart-theme` + `ChartTooltip` sur un petit LineChart/BarChart Recharts, en light ET dark, pour confirmer : axes/grille recessifs, tooltip lisible sur surface, pastilles de série distinctes, légende présente dès 2 séries. **Ne redesigner AUCUN dashboard existant** (`DashboardPage`, `VolumeTrendSection` restent tels quels — hors périmètre). Retirer l'exemple avant de committer.

- [ ] **Step 12 : Commit**

```bash
git add frontend/scripts/dataviz/validate_palette.js frontend/src/lib/chart-theme.ts frontend/src/components/ui/ChartTooltip.tsx frontend/src/test/lib/chart-theme.test.ts frontend/src/test/components/ChartTooltip.test.tsx
git commit -m "feat(charts): thème Recharts + palette séries validée par script + ChartTooltip"
```

---

## Après le lot (décision utilisateur — NE PAS pousser sans go-ahead)

- Tous les critères d'acceptation de la spec cochés ; vitest 100 % ; tsc 0 ; backend inchangé.
- **Demander à l'utilisateur** : push de la branche `feat/design-system-foundation` et/ou ouverture de PR (règle projet : décision push/PR à la main). Ne pas pousser de façon autonome.
- Rappel règle « push tous les 10 commits » : ce lot fait 6 commits (T1-T6) + le commit de spec déjà présent = 7 ; pas de push forcé requis avant la décision utilisateur.

---

## Auto-revue (writing-plans)

**1. Couverture de la spec :**
- §3 Unification tokens → T1 (mapping `:root` + `.dark` `--primary`/`--accent`, garanties anti-casse, vérif rendu light/dark). ✅
- §4a Button → T2 (5 variants dont `gold`, sizes, loading, focus, forwardRef). ✅
- §4b Input → T3 ✅ ; §4c Select → T3 (`<select>` natif, chevron SVG). ✅
- §4d Table → T4 (sous-composants, `.num` via className, overflow-x-auto). ✅
- §4e Card + alias Panel → T5 (Panel conservé distinct/rétro-compat) ; Tabs maison a11y clavier → T5. ✅
- §5a chart-theme.ts → T6 step 7 ; §5b palette dédiée validée par script → T6 steps 1-4/7 ; §5c ChartTooltip → T6 step 8 ; §5d wrappers = **optionnels**, non retenus (YAGNI — la spec les dit « optionnels » ; `chartGridProps`/`chartAxisProps` couvrent la réduction de répétition sans wrapper). ✅
- §6 hors périmètre → rappelé (aucun dashboard redesigné, pas de fonte, pas de dépendance, pas de Dialog). ✅
- §7 critères d'acceptation → couverts par les gates de chaque tâche + vérifs rendu. ✅

**2. Placeholders :** les palettes de T6 sont explicitement marquées « à valider/remplacer par script » avec la procédure exacte — ce n'est pas un TODO flou mais une étape calculée avec commande et critère de passage. Aucun « add error handling » vague. ✅

**3. Cohérence des types :** `buttonVariants`/`ButtonProps` (T2) ; `InputProps`/`SelectProps` avec `error?` (T3) ; sous-composants Table `forwardRef` (T4) ; `Card*` + `Tabs`/`TabList`/`Tab`/`TabPanel` avec contexte (T5) ; `getSeriesColor(index, dark?)` + `SERIES_PALETTE_LIGHT/DARK` + `ChartTooltipProps` (T6) — noms cohérents entre définition et tests. ✅

**Note reportée à l'utilisateur :** `VolumeTrendSection.tsx` viole deux non-négociables dataviz (dual-axis gauche/droite + hex de série codées en dur `#6366f1`/`#10b981`). C'est **hors périmètre de ce lot** (aucun dashboard redesigné) mais à flaguer pour un lot d'adoption ultérieur.
