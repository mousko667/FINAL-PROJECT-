# Design System — Lot 2 (Adoption) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refondre la dataviz de `VolumeTrendSection` selon le skill dataviz (supprimer le dual-axis + les hex codés en dur) et solder les 4 dettes MINOR héritées du Lot 1.

**Architecture:** Front-only. On consomme la fondation du Lot 1 (`chart-theme.ts`, `ChartTooltip`, primitives `ui/`) sans ajouter de dépendance. `VolumeTrendSection` passe d'un `ComposedChart` dual-axis à deux graphiques empilés (small multiples) partageant l'axe X, chacun avec un axe Y unique et une couleur issue de `getSeriesColor`. Les 4 MINOR sont des retouches ciblées sur `Table.tsx`, `Tabs.tsx`, `chart-theme.ts` et `Button.test.tsx`.

**Tech Stack:** React 19 + TS, Recharts, react-i18next, Tailwind, vitest + @testing-library/react, cva, cn().

## Global Constraints

- **Idiome maison figé** : natif stylé, ZÉRO dépendance nouvelle ; `cn()` depuis `@/lib/utils` ; `cva` ; `forwardRef` ; radius `rounded-[4px]` ; focus `focus-visible:ring-ring` + `ring-offset-2` SANS `ring-offset-background`. Montants en classe `.num`.
- **Devise = XAF** (JAMAIS XOF).
- **Aucun texte en dur** : tout libellé passe par `t('clé', 'fallback FR')` (react-i18next) — les clés existent déjà en fallback inline dans `VolumeTrendSection`, on les réutilise verbatim.
- **Dataviz non-négociables** : un seul axe Y par graphique (jamais de dual-axis) ; couleur de série via `getSeriesColor(index)` (l'entité, pas le rang), jamais un hex codé ; grille/axes récessifs via `chartGridProps`/`chartAxisProps` ; légende présente pour ≥ 2 séries seulement (ici 1 série par graphe → pas de légende, le titre nomme la série) ; texte en tokens d'encre, jamais la couleur de série.
- **Gate VERT avant chaque commit** (depuis `frontend/`) : `npx vitest run` 100 % + `npx tsc --noEmit` 0 erreur. Baseline Lot 1 = **181/181**, tsc 0.
- **Un commit par tâche**, message `type(scope): desc`. Branche courante : `feat/design-system-adoption` (depuis `main`). Ne PAS pousser sans go-ahead user (push/PR décidés en fin de lot).
- Toutes les commandes shell se lancent depuis `frontend/` : `cd "C:\Users\Dany\Documents\FINAL PROJECT\invoice-system\frontend"` (PowerShell) ou `cd "/c/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"` (Bash tool, préfixer chaque commande).

---

## File Structure

- `frontend/src/components/reports/VolumeTrendSection.tsx` — **Modifié** (Tâche 1) : remplace le `ComposedChart` dual-axis par deux `ResponsiveContainer` empilés (BarChart montant + LineChart nombre), branche `chart-theme` + `ChartTooltip`, ajoute `data-testid` sur chaque sous-graphe.
- `frontend/src/test/components/VolumeTrendSection.test.tsx` — **Modifié** (Tâche 1) : conserve les 2 tests existants, ajoute l'assertion des 2 sous-graphiques.
- `frontend/src/components/ui/Table.tsx` — **Modifié** (Tâche 2) : ajoute `containerClassName` sur `Table`.
- `frontend/src/test/components/Table.test.tsx` — **Modifié** (Tâche 2) : test que `containerClassName` atterrit sur le wrapper et `className` sur le `<table>`.
- `frontend/src/components/ui/Tabs.tsx` — **Modifié** (Tâche 3) : `ring-offset-1` → `ring-offset-2` sur `Tab`.
- `frontend/src/lib/chart-theme.ts` — **Modifié** (Tâche 4) : `getSeriesColor` borne l'index négatif à 0 (ceinture-bretelles).
- `frontend/src/test/lib/chart-theme.test.ts` — **Modifié** (Tâche 4) : test index négatif → slot 0.
- `frontend/src/test/components/Button.test.tsx` — **Modifié** (Tâche 5) : couvre variants `secondary`/`ghost`, sizes `sm`/`lg`/`icon`, spinner.

---

### Task 1: Refonte dataviz VolumeTrendSection (small multiples, conforme dataviz)

**Files:**
- Modify: `frontend/src/components/reports/VolumeTrendSection.tsx`
- Test: `frontend/src/test/components/VolumeTrendSection.test.tsx`

**Interfaces:**
- Consumes (Lot 1) : `getSeriesColor(index: number, dark?: boolean): string`, `chartGridProps`, `chartAxisProps` depuis `@/lib/chart-theme` ; `ChartTooltip` (props `{ active, payload, label, valueFormatter }`) depuis `@/components/ui/ChartTooltip` ; `formatAmount(n)` depuis `@/lib/format`.
- Produces : rien pour les tâches suivantes (feuille).

**Décision de conception (validée user + skill dataviz)** : le dual-axis (montant XAF vs comptage) est l'anti-pattern dataviz #1. On sépare en **deux graphiques empilés (small multiples)** partageant l'axe X (mois), chacun avec un axe Y unique. Une seule série par graphe → **pas de légende** (le titre de chaque sous-bloc nomme la série). Couleurs via `getSeriesColor(0)` (montant) et `getSeriesColor(1)` (nombre) — plus aucun hex codé.

**Note dark mode** : `getSeriesColor(index, dark)` a besoin de savoir si on est en dark. Dans ce projet le dark est porté par la classe `.dark` sur `<html>` (hook `useTheme`). Recharts reçoit une couleur résolue au rendu : on lit le mode via `document.documentElement.classList.contains('dark')` au rendu du composant. Comme le composant se re-rend au toggle de thème (le hook applique la classe et déclenche un re-render de l'app), la couleur suivra. Pour rester testable et simple, on calcule `const dark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')` en tête de composant.

- [ ] **Step 1: Mettre à jour le test — assertion des deux sous-graphiques**

Remplacer le premier test (`renders the trend section title once loaded`) pour vérifier AUSSI que les deux sous-graphiques sont rendus via `data-testid`. Conserver l'import et `renderSection()` tels quels. Le test de l'état vide reste inchangé.

Dans `frontend/src/test/components/VolumeTrendSection.test.tsx`, remplacer le bloc `it('renders the trend section title once loaded', ...)` par :

```tsx
  it('renders the two small-multiple charts once loaded (no dual-axis)', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleTrend } })
    renderSection()
    expect(await screen.findByText('Tendances volume / valeur')).toBeInTheDocument()
    // Deux graphiques distincts (montant + nombre), chacun son axe Y unique.
    expect(screen.getByTestId('volume-trend-amount')).toBeInTheDocument()
    expect(screen.getByTestId('volume-trend-count')).toBeInTheDocument()
  })
```

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `npx vitest run src/test/components/VolumeTrendSection.test.tsx`
Expected: FAIL — `Unable to find an element by: [data-testid="volume-trend-amount"]` (le composant rend encore un seul ComposedChart).

- [ ] **Step 3: Réécrire VolumeTrendSection en small multiples**

Remplacer intégralement le contenu de `frontend/src/components/reports/VolumeTrendSection.tsx` par :

```tsx
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2 } from 'lucide-react'
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatAmount } from '@/lib/format'
import { getSeriesColor, chartGridProps, chartAxisProps } from '@/lib/chart-theme'
import { ChartTooltip } from '@/components/ui/ChartTooltip'

interface TrendPoint {
  monthLabel: string
  year: number
  month: number
  invoiceCount: number
  totalAmount: number
}
interface VolumeTrend {
  fromDate: string
  toDate: string
  points: TrendPoint[]
}

export default function VolumeTrendSection() {
  const { t } = useTranslation()
  const dark = typeof document !== 'undefined'
    && document.documentElement.classList.contains('dark')

  const { data: trend, isLoading } = useQuery({
    queryKey: ['volume-trend'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: VolumeTrend }>('/reports/volume-trend')
      return data.data
    },
    retry: false,
  })

  const isEmpty = !trend?.points?.length
    || trend.points.every(p => p.invoiceCount === 0 && p.totalAmount === 0)

  if (isLoading) {
    return (
      <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
        <div className="px-5 py-4">
          <div className="flex justify-center py-4">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        </div>
      </div>
    )
  }

  const amountColor = getSeriesColor(0, dark)
  const countColor = getSeriesColor(1, dark)

  return (
    <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
      <div className="px-5 py-4 font-semibold text-ink">{t('reports.trends.title', 'Tendances volume / valeur')}</div>
      <div className="border-t px-5 py-4">
        <p className="text-sm text-ink-soft mb-4">{t('reports.trends.desc', 'Nombre de factures et montant total par mois sur les 12 derniers mois (par date de facture).')}</p>
        {isEmpty ? (
          <p data-testid="volume-trend-empty" className="text-sm text-center text-ink-faint py-4">{t('reports.noData', 'Aucune donnée')}</p>
        ) : (
          <div className="space-y-6">
            <div data-testid="volume-trend-amount">
              <div className="text-xs font-medium text-ink-soft mb-2">{t('reports.trends.amount', 'Montant (XAF)')}</div>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={trend!.points}>
                  <CartesianGrid {...chartGridProps} />
                  <XAxis dataKey="monthLabel" {...chartAxisProps} />
                  <YAxis {...chartAxisProps} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--hairline) / 0.4)' }}
                    content={<ChartTooltip valueFormatter={v => `${formatAmount(v)} XAF`} />}
                  />
                  <Bar dataKey="totalAmount" name={t('reports.trends.amount', 'Montant (XAF)')} fill={amountColor} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div data-testid="volume-trend-count">
              <div className="text-xs font-medium text-ink-soft mb-2">{t('reports.trends.count', 'Nb de factures')}</div>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={trend!.points}>
                  <CartesianGrid {...chartGridProps} />
                  <XAxis dataKey="monthLabel" {...chartAxisProps} />
                  <YAxis {...chartAxisProps} allowDecimals={false} />
                  <Tooltip
                    cursor={{ stroke: 'hsl(var(--hairline))' }}
                    content={<ChartTooltip />}
                  />
                  <Line dataKey="invoiceCount" name={t('reports.trends.count', 'Nb de factures')} stroke={countColor} strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Lancer le test du composant pour le voir passer**

Run: `npx vitest run src/test/components/VolumeTrendSection.test.tsx`
Expected: PASS (3 tests : les 2 sous-graphes rendus, titre, état vide).

- [ ] **Step 5: Gate complète (vitest + tsc) puis commit**

Run: `npx vitest run` → Expected: 100 % vert (≥ 181 + delta ; aucune régression).
Run: `npx tsc --noEmit` → Expected: 0 erreur.

```bash
git add frontend/src/components/reports/VolumeTrendSection.tsx frontend/src/test/components/VolumeTrendSection.test.tsx
git commit -m "refactor(charts): VolumeTrendSection en small multiples (supprime dual-axis + hex codes, theme dark-safe)"
```

---

### Task 2: Table — prop `containerClassName` (rétro-compatible)

**Files:**
- Modify: `frontend/src/components/ui/Table.tsx:10-18`
- Test: `frontend/src/test/components/Table.test.tsx`

**Interfaces:**
- Produces : `Table` accepte désormais une prop optionnelle `containerClassName?: string` appliquée au `<div className="w-full overflow-x-auto">` wrapper ; `className` continue d'atterrir sur le `<table>` (comportement inchangé, rétro-compatible).

- [ ] **Step 1: Écrire le test qui échoue**

Vérifier que `containerClassName` va sur le wrapper et `className` sur le `<table>`. Ajouter ce test au fichier `frontend/src/test/components/Table.test.tsx` (dans le `describe('Table', ...)` existant — l'ajouter à la fin du bloc) :

```tsx
  it('applique containerClassName au wrapper overflow et className au <table>', () => {
    const { container } = render(
      <Table className="table-cls" containerClassName="wrapper-cls">
        <TBody><TR><TD>x</TD></TR></TBody>
      </Table>
    )
    const wrapper = container.querySelector('div.wrapper-cls')
    expect(wrapper).not.toBeNull()
    expect(wrapper?.className).toMatch(/overflow-x-auto/)
    const table = container.querySelector('table')
    expect(table?.className).toMatch(/table-cls/)
    expect(wrapper?.className).not.toMatch(/table-cls/)
  })
```

Vérifier en tête de fichier que `Table, TBody, TR, TD` sont importés ; sinon compléter l'import existant `import { Table, ... } from '@/components/ui/Table'`.

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `npx vitest run src/test/components/Table.test.tsx`
Expected: FAIL — `containerClassName` est actuellement passé au `<table>` via `{...props}` (React l'ignore comme attribut inconnu), le wrapper n'a pas la classe.

- [ ] **Step 3: Ajouter la prop `containerClassName`**

Remplacer le bloc `Table` (lignes 10-18) de `frontend/src/components/ui/Table.tsx` par :

```tsx
export const Table = React.forwardRef<
  HTMLTableElement,
  React.TableHTMLAttributes<HTMLTableElement> & { containerClassName?: string }
>(({ className, containerClassName, ...props }, ref) => (
  <div className={cn('w-full overflow-x-auto', containerClassName)}>
    <table ref={ref} className={cn('w-full border-collapse text-sm', className)} {...props} />
  </div>
))
Table.displayName = 'Table'
```

- [ ] **Step 4: Lancer le test pour le voir passer**

Run: `npx vitest run src/test/components/Table.test.tsx`
Expected: PASS.

- [ ] **Step 5: Gate complète puis commit**

Run: `npx vitest run` → Expected: 100 % vert.
Run: `npx tsc --noEmit` → Expected: 0 erreur.

```bash
git add frontend/src/components/ui/Table.tsx frontend/src/test/components/Table.test.tsx
git commit -m "feat(ui): Table accepte containerClassName (cible le wrapper overflow, retro-compatible)"
```

---

### Task 3: Tabs — harmoniser le focus ring-offset (1px → 2px)

**Files:**
- Modify: `frontend/src/components/ui/Tabs.tsx:114`
- Test: `frontend/src/test/components/Tabs.test.tsx`

**Interfaces:**
- Produces : le `Tab` actif porte `focus-visible:ring-offset-2` (aligné sur `Button`), plus `ring-offset-1`.

- [ ] **Step 1: Écrire le test qui échoue**

Ajouter au `describe` existant de `frontend/src/test/components/Tabs.test.tsx` un test vérifiant l'offset harmonisé. Le fichier rend déjà des `Tabs`/`TabList`/`Tab` (réutiliser le même harnais de rendu que les tests voisins du fichier) :

```tsx
  it('utilise ring-offset-2 au focus (harmonise avec Button)', () => {
    render(
      <Tabs defaultValue="a">
        <TabList>
          <Tab value="a">Onglet A</Tab>
          <Tab value="b">Onglet B</Tab>
        </TabList>
      </Tabs>
    )
    const tab = screen.getByRole('tab', { name: 'Onglet A' })
    expect(tab.className).toMatch(/focus-visible:ring-offset-2/)
    expect(tab.className).not.toMatch(/ring-offset-1/)
  })
```

Vérifier que `Tabs, TabList, Tab` et `render`, `screen` sont importés en tête du fichier ; compléter l'import si besoin.

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `npx vitest run src/test/components/Tabs.test.tsx`
Expected: FAIL — la classe contient `ring-offset-1`, pas `ring-offset-2`.

- [ ] **Step 3: Corriger l'offset**

Dans `frontend/src/components/ui/Tabs.tsx`, ligne 114, remplacer :

```tsx
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1',
```

par :

```tsx
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
```

- [ ] **Step 4: Lancer le test pour le voir passer**

Run: `npx vitest run src/test/components/Tabs.test.tsx`
Expected: PASS.

- [ ] **Step 5: Gate complète puis commit**

Run: `npx vitest run` → Expected: 100 % vert.
Run: `npx tsc --noEmit` → Expected: 0 erreur.

```bash
git add frontend/src/components/ui/Tabs.tsx frontend/src/test/components/Tabs.test.tsx
git commit -m "fix(ui): Tabs focus ring-offset-2 (harmonise avec Button)"
```

---

### Task 4: chart-theme — borner l'index négatif dans getSeriesColor

**Files:**
- Modify: `frontend/src/lib/chart-theme.ts:23-26`
- Test: `frontend/src/test/lib/chart-theme.test.ts`

**Interfaces:**
- Produces : `getSeriesColor(index, dark?)` renvoie le slot 0 pour tout index < 0 (au lieu de `undefined`) ; comportement inchangé pour index ≥ 0 (repli sur le dernier slot au-delà de la longueur).

- [ ] **Step 1: Écrire le test qui échoue**

Ajouter au `describe` existant de `frontend/src/test/lib/chart-theme.test.ts` :

```ts
  it('borne un index négatif au premier slot (jamais undefined)', () => {
    expect(getSeriesColor(-1)).toBe(SERIES_PALETTE_LIGHT[0])
    expect(getSeriesColor(-5, true)).toBe(SERIES_PALETTE_DARK[0])
  })
```

Vérifier que `getSeriesColor`, `SERIES_PALETTE_LIGHT`, `SERIES_PALETTE_DARK` sont importés en tête du fichier ; compléter l'import si besoin.

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run: `npx vitest run src/test/lib/chart-theme.test.ts`
Expected: FAIL — `getSeriesColor(-1)` renvoie `undefined` (`palette[Math.min(-1, 7)]` = `palette[-1]`).

- [ ] **Step 3: Borner l'index**

Dans `frontend/src/lib/chart-theme.ts`, remplacer le corps de `getSeriesColor` (lignes 23-26) par :

```ts
export function getSeriesColor(index: number, dark = false): string {
  const palette = dark ? SERIES_PALETTE_DARK : SERIES_PALETTE_LIGHT
  const i = Math.max(0, Math.min(index, palette.length - 1))
  return palette[i]
}
```

- [ ] **Step 4: Lancer le test pour le voir passer**

Run: `npx vitest run src/test/lib/chart-theme.test.ts`
Expected: PASS.

- [ ] **Step 5: Gate complète puis commit**

Run: `npx vitest run` → Expected: 100 % vert.
Run: `npx tsc --noEmit` → Expected: 0 erreur.

```bash
git add frontend/src/lib/chart-theme.ts frontend/src/test/lib/chart-theme.test.ts
git commit -m "fix(charts): getSeriesColor borne l'index negatif au premier slot"
```

---

### Task 5: Button.test — compléter la couverture (variants, sizes, spinner)

**Files:**
- Test: `frontend/src/test/components/Button.test.tsx`

**Interfaces:**
- Consumes : `buttonVariants` classes connues — `secondary` → `border-hairline` + `bg-surface` ; `ghost` → `bg-transparent` ; sizes `sm` → `h-8`, `lg` → `h-12`, `icon` → `h-10 w-10` ; spinner `loading` → un SVG `animate-spin` présent dans le bouton.
- Produces : rien (fichier de test seul).

- [ ] **Step 1: Ajouter les tests de couverture**

Ajouter ces `it(...)` à la fin du `describe('Button', ...)` de `frontend/src/test/components/Button.test.tsx` (ne rien supprimer) :

```tsx
  it('applique la variante secondary (surface + bordure hairline)', () => {
    render(<Button variant="secondary">x</Button>)
    const cls = screen.getByRole('button').className
    expect(cls).toMatch(/bg-surface/)
    expect(cls).toMatch(/border-hairline/)
  })

  it('applique la variante ghost (fond transparent)', () => {
    render(<Button variant="ghost">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-transparent/)
  })

  it('applique la taille sm (h-8)', () => {
    render(<Button size="sm">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/h-8/)
  })

  it('applique la taille lg (h-12)', () => {
    render(<Button size="lg">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/h-12/)
  })

  it('applique la taille icon (carré h-10 w-10)', () => {
    render(<Button size="icon" aria-label="fermer">x</Button>)
    const cls = screen.getByRole('button', { name: 'fermer' }).className
    expect(cls).toMatch(/h-10/)
    expect(cls).toMatch(/w-10/)
  })

  it('affiche un spinner animé en état loading', () => {
    const { container } = render(<Button loading>x</Button>)
    expect(container.querySelector('svg.animate-spin')).not.toBeNull()
  })
```

- [ ] **Step 2: Lancer le test pour le voir passer (couverture, pas de code prod à changer)**

Run: `npx vitest run src/test/components/Button.test.tsx`
Expected: PASS (les 8 anciens + 6 nouveaux = 14 tests). Ces tests décrivent le comportement déjà implémenté de `Button`, donc ils passent directement — c'est de la couverture rétroactive, pas du TDD à rouge préalable.

- [ ] **Step 3: Gate complète puis commit**

Run: `npx vitest run` → Expected: 100 % vert.
Run: `npx tsc --noEmit` → Expected: 0 erreur.

```bash
git add frontend/src/test/components/Button.test.tsx
git commit -m "test(ui): couvre variants secondary/ghost, sizes sm/lg/icon et spinner du Button"
```

---

## Clôture du lot (après les 5 tâches)

- [ ] **Revue whole-branch (opus)** via superpowers:requesting-code-review sur le diff complet `main..feat/design-system-adoption`.
- [ ] **Vérif runtime** (`npm run dev`, backend 8080) : ouvrir la page Rapports, vérifier les deux graphiques VolumeTrend en light PUIS en dark (`localStorage['oct-theme']='dark'` + reload). Playwright dispo.
- [ ] **Décision push / PR à DEMANDER au user** (ne pas pousser sans go-ahead). PR en fin de lot si go.

---

## Self-Review

**1. Spec coverage** — Périmètre tranché = (a) refonte dataviz VolumeTrendSection → Tâche 1 ; (b) 4 MINOR : Button.test → Tâche 5, Table containerClassName → Tâche 2, Tabs ring-offset → Tâche 3, getSeriesColor négatif → Tâche 4. Les 4 MINOR + l'item dataviz sont tous couverts.

**2. Placeholder scan** — Chaque step de code montre le code complet ; chaque commande a un résultat attendu ; aucun "TBD/TODO/handle edge cases". OK.

**3. Type consistency** — `getSeriesColor(index, dark)` : signature identique à l'existant (Tâche 1 la consomme, Tâche 4 la modifie sans changer la signature publique). `containerClassName?: string` (Tâche 2) cohérent entre code et test. `ChartTooltip` props `{ active, payload, label, valueFormatter }` : Tâche 1 ne passe que `valueFormatter`, conforme à la signature Lot 1. OK.
