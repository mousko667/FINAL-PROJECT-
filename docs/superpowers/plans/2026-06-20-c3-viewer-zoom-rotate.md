# C3 — Contrôles zoom/rotate du visualiseur de documents — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Doter `DocumentViewerModal` de contrôles zoom / rotation / reset réels, pour les PDF (via react-pdf) et les images (via transform CSS), sans changer sa signature publique.

**Architecture:** La modale monolithique est découpée en unités à responsabilité unique : un conteneur qui détient l'état (`DocumentViewerModal`), une barre d'outils présentationnelle (`ViewerToolbar`), un rendu PDF encapsulant react-pdf (`PdfDocument`), une branche image en `transform` CSS, et un module de config du worker pdf.js (`pdfWorker.ts`). État unidirectionnel descendant.

**Tech Stack:** React 19.2, TypeScript, Vite 8, vitest + @testing-library/react, react-i18next, react-pdf (pdfjs-dist), lucide-react, Tailwind.

## Global Constraints

- React **19.2** + Vite **8** : `react-pdf` retenu doit supporter React 19 (react-pdf ≥ 9.x).
- Signature publique de `DocumentViewerModal` **inchangée** : `{ url, filename, fileType?, onClose }`. `InvoiceDetailPage.tsx` ne doit PAS être modifié.
- i18n frontend = `frontend/src/i18n/fr.json` + `en.json` (UTF-8). Parité fr/en obligatoire. Bloc `invoice.viewer` existe déjà (`fr.json:278`).
- Bornes zoom : **0.5 → 3.0**, défaut **1.0**, pas **0.25**. Rotation horaire `(r+90)%360`, valeurs `0|90|180|270`.
- Tests : `react-pdf` **mocké** via `vi.mock` (worker/canvas ne tournent pas sous jsdom). TDD RED→GREEN.
- Vérif finale : `npx tsc --noEmit`, `npm run build` (vite), `npx vitest run` tous verts.
- Commits via `git commit -F -` (here-doc bash) — pas de here-string PowerShell.
- Tout bug réel → `docs/KNOWN_ISSUES_REGISTRY.md` AVANT le commit concerné.
- Répertoire de travail : `c:/Users/Dany/Documents/FINAL PROJECT/invoice-system`. Branche `fix/a1-cashflow-sqlgrammar` (NE PAS pousser).

---

## File Structure

- Create `frontend/src/components/invoice/pdfWorker.ts` — configure `pdfjs.GlobalWorkerOptions.workerSrc` (effet d'import unique).
- Create `frontend/src/components/invoice/ViewerToolbar.tsx` — barre d'outils présentationnelle (zoom/rotate/reset + nav pages).
- Create `frontend/src/components/invoice/PdfDocument.tsx` — encapsule `<Document>/<Page>` react-pdf.
- Modify `frontend/src/components/invoice/DocumentViewerModal.tsx` — conteneur d'état, monte toolbar + rendu.
- Create `frontend/src/test/components/DocumentViewerModal.test.tsx` — tests vitest (react-pdf mocké).
- Modify `frontend/src/i18n/fr.json` + `en.json` — clés `invoice.viewer.*`.
- Modify `frontend/package.json` (via npm install) — dépendances react-pdf.
- Modify `docs/COMPLIANCE_MATRIX.md` — basculer M9 #4 ✅.

---

## Task 1 : Installer react-pdf + configurer le worker pdf.js

**Files:**
- Modify: `frontend/package.json` (via `npm install`)
- Create: `frontend/src/components/invoice/pdfWorker.ts`

**Interfaces:**
- Consumes: rien.
- Produces: module `pdfWorker.ts` à importer (effet de bord) avant tout rendu react-pdf. Exporte `{ Document, Page, pdfjs }` ré-exportés depuis `react-pdf` pour un point d'import unique.

- [ ] **Step 1: Installer la dépendance compatible React 19**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npm install react-pdf@^9
```
Attendu : `react-pdf` et `pdfjs-dist` ajoutés à `dependencies`, install OK. Si un conflit de peer dependency React 19 apparaît, vérifier la dernière majeure de react-pdf supportant React 19 et l'utiliser (ne PAS forcer `--legacy-peer-deps` sans vérification).

- [ ] **Step 2: Créer le module worker**

`frontend/src/components/invoice/pdfWorker.ts` :
```ts
import { Document, Page, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'

// Worker pdf.js servi en asset par Vite (URL résolue au build).
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

export { Document, Page, pdfjs }
```
Note : si le chemin `pdf.worker.min.mjs` n'existe pas dans la version installée, lister `node_modules/pdfjs-dist/build/` et utiliser le nom de fichier worker réellement présent.

- [ ] **Step 3: Vérifier la compilation**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx tsc --noEmit
```
Attendu : 0 erreur.

- [ ] **Step 4: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/package.json frontend/package-lock.json frontend/src/components/invoice/pdfWorker.ts
git commit -F - <<'EOF'
build(c3): ajout react-pdf + config worker pdf.js pour Vite

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 2 : Clés i18n du visualiseur

**Files:**
- Modify: `frontend/src/i18n/fr.json` (bloc `invoice.viewer`, ~ligne 278)
- Modify: `frontend/src/i18n/en.json` (bloc `invoice.viewer` correspondant)

**Interfaces:**
- Consumes: rien.
- Produces: clés `invoice.viewer.zoomIn`, `zoomOut`, `rotate`, `resetView`, `page`, `of`, `loadError` (fr + en).

- [ ] **Step 1: Ajouter les clés FR**

Dans `frontend/src/i18n/fr.json`, le bloc existant est :
```json
    "viewer": {
      "view": "Aperçu",
      "noPreview": "Aperçu non disponible pour ce type de fichier."
    },
```
Le remplacer par :
```json
    "viewer": {
      "view": "Aperçu",
      "noPreview": "Aperçu non disponible pour ce type de fichier.",
      "zoomIn": "Zoom avant",
      "zoomOut": "Zoom arrière",
      "rotate": "Pivoter",
      "resetView": "Réinitialiser",
      "page": "Page",
      "of": "sur",
      "loadError": "Impossible de charger le document."
    },
```

- [ ] **Step 2: Ajouter les clés EN**

Localiser le bloc `viewer` dans `frontend/src/i18n/en.json` (mêmes clés `view`/`noPreview`) et y ajouter les mêmes clés avec les valeurs anglaises :
```json
      "zoomIn": "Zoom in",
      "zoomOut": "Zoom out",
      "rotate": "Rotate",
      "resetView": "Reset",
      "page": "Page",
      "of": "of",
      "loadError": "Unable to load the document."
```
(insérées après `noPreview`, en respectant la syntaxe JSON existante du fichier).

- [ ] **Step 3: Vérifier la validité JSON + parité**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
node -e "const f=require('./src/i18n/fr.json').invoice.viewer, e=require('./src/i18n/en.json').invoice.viewer; const a=Object.keys(f).sort(), b=Object.keys(e).sort(); if(JSON.stringify(a)!==JSON.stringify(b)){console.error('PARITÉ KO', a, b); process.exit(1)} console.log('parité OK', a)"
```
Attendu : `parité OK [ ... 9 clés ... ]`.

- [ ] **Step 4: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -F - <<'EOF'
i18n(c3): clés du visualiseur (zoom/rotate/reset/pagination)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 3 : `ViewerToolbar` (présentationnel) — TDD

**Files:**
- Create: `frontend/src/components/invoice/ViewerToolbar.tsx`
- Test: `frontend/src/test/components/DocumentViewerModal.test.tsx` (créé ici, sous-bloc `ViewerToolbar`)

**Interfaces:**
- Consumes: clés i18n de Task 2.
- Produces:
```ts
export interface ViewerToolbarProps {
  zoom: number            // ex. 1.0
  rotation: number        // 0 | 90 | 180 | 270
  canZoomIn: boolean
  canZoomOut: boolean
  pageNumber?: number     // PDF uniquement
  numPages?: number       // PDF uniquement ; nav affichée si > 1
  onZoomIn: () => void
  onZoomOut: () => void
  onRotate: () => void
  onReset: () => void
  onPrevPage?: () => void
  onNextPage?: () => void
}
export function ViewerToolbar(props: ViewerToolbarProps): JSX.Element
```

- [ ] **Step 1: Écrire les tests RED**

Créer `frontend/src/test/components/DocumentViewerModal.test.tsx` :
```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { ViewerToolbar } from '@/components/invoice/ViewerToolbar'

function renderToolbar(overrides = {}) {
  const props = {
    zoom: 1, rotation: 0, canZoomIn: true, canZoomOut: true,
    onZoomIn: vi.fn(), onZoomOut: vi.fn(), onRotate: vi.fn(), onReset: vi.fn(),
    ...overrides,
  }
  render(<I18nextProvider i18n={i18n}><ViewerToolbar {...props} /></I18nextProvider>)
  return props
}

describe('ViewerToolbar', () => {
  it('affiche le niveau de zoom en pourcentage', () => {
    renderToolbar({ zoom: 1.5 })
    expect(screen.getByText('150%')).toBeDefined()
  })

  it('déclenche les callbacks zoom/rotate/reset', async () => {
    const u = userEvent.setup()
    const p = renderToolbar()
    await u.click(screen.getByLabelText(/zoom avant/i)); expect(p.onZoomIn).toHaveBeenCalled()
    await u.click(screen.getByLabelText(/zoom arrière/i)); expect(p.onZoomOut).toHaveBeenCalled()
    await u.click(screen.getByLabelText(/pivoter/i)); expect(p.onRotate).toHaveBeenCalled()
    await u.click(screen.getByLabelText(/réinitialiser/i)); expect(p.onReset).toHaveBeenCalled()
  })

  it('désactive zoom+ / zoom- aux bornes', () => {
    renderToolbar({ canZoomIn: false, canZoomOut: false })
    expect(screen.getByLabelText(/zoom avant/i)).toHaveProperty('disabled', true)
    expect(screen.getByLabelText(/zoom arrière/i)).toHaveProperty('disabled', true)
  })

  it('masque la navigation pages en monopage', () => {
    renderToolbar({ pageNumber: 1, numPages: 1 })
    expect(screen.queryByText(/sur/i)).toBeNull()
  })

  it('affiche la navigation pages en multipage', () => {
    renderToolbar({ pageNumber: 2, numPages: 5 })
    expect(screen.getByText(/2/)).toBeDefined()
    expect(screen.getByText(/sur/i)).toBeDefined()
    expect(screen.getByText(/5/)).toBeDefined()
  })
})
```

- [ ] **Step 2: Lancer → échec attendu**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx vitest run src/test/components/DocumentViewerModal.test.tsx
```
Attendu : FAIL (`ViewerToolbar` introuvable).

- [ ] **Step 3: Implémenter `ViewerToolbar`**

`frontend/src/components/invoice/ViewerToolbar.tsx` :
```tsx
import { useTranslation } from 'react-i18next'
import { ZoomIn, ZoomOut, RotateCw, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react'

export interface ViewerToolbarProps {
  zoom: number
  rotation: number
  canZoomIn: boolean
  canZoomOut: boolean
  pageNumber?: number
  numPages?: number
  onZoomIn: () => void
  onZoomOut: () => void
  onRotate: () => void
  onReset: () => void
  onPrevPage?: () => void
  onNextPage?: () => void
}

export function ViewerToolbar(props: ViewerToolbarProps) {
  const { t } = useTranslation()
  const { zoom, canZoomIn, canZoomOut, pageNumber, numPages } = props
  const multiPage = (numPages ?? 0) > 1
  const btn = 'p-1.5 rounded text-gray-600 hover:bg-gray-200 disabled:opacity-40 disabled:cursor-not-allowed'

  return (
    <div className="flex items-center gap-1 px-3 py-2 border-b bg-gray-50">
      <button type="button" className={btn} aria-label={t('invoice.viewer.zoomOut', 'Zoom arrière')}
        onClick={props.onZoomOut} disabled={!canZoomOut}><ZoomOut className="w-4 h-4" /></button>
      <span className="text-xs tabular-nums w-12 text-center">{Math.round(zoom * 100)}%</span>
      <button type="button" className={btn} aria-label={t('invoice.viewer.zoomIn', 'Zoom avant')}
        onClick={props.onZoomIn} disabled={!canZoomIn}><ZoomIn className="w-4 h-4" /></button>
      <button type="button" className={btn} aria-label={t('invoice.viewer.rotate', 'Pivoter')}
        onClick={props.onRotate}><RotateCw className="w-4 h-4" /></button>
      <button type="button" className={btn} aria-label={t('invoice.viewer.resetView', 'Réinitialiser')}
        onClick={props.onReset}><RefreshCw className="w-4 h-4" /></button>

      {multiPage && (
        <div className="flex items-center gap-1 ml-auto text-xs text-gray-600">
          <button type="button" className={btn} aria-label={t('invoice.viewer.zoomOut', 'Page précédente')}
            onClick={props.onPrevPage} disabled={(pageNumber ?? 1) <= 1}><ChevronLeft className="w-4 h-4" /></button>
          <span>{t('invoice.viewer.page', 'Page')} {pageNumber} {t('invoice.viewer.of', 'sur')} {numPages}</span>
          <button type="button" className={btn} aria-label={t('invoice.viewer.rotate', 'Page suivante')}
            onClick={props.onNextPage} disabled={(pageNumber ?? 1) >= (numPages ?? 1)}><ChevronRight className="w-4 h-4" /></button>
        </div>
      )}
    </div>
  )
}
```
Note : `aria-label` des boutons page réutilise des clés existantes faute de libellé dédié ; le test cible le texte « Page X sur N », pas ces labels. Ne pas ajouter de clés non prévues au spec.

- [ ] **Step 4: Lancer → succès attendu**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx vitest run src/test/components/DocumentViewerModal.test.tsx
```
Attendu : PASS (bloc ViewerToolbar).

- [ ] **Step 5: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/components/invoice/ViewerToolbar.tsx frontend/src/test/components/DocumentViewerModal.test.tsx
git commit -F - <<'EOF'
feat(c3): barre d'outils visualiseur (zoom/rotate/reset/pagination)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 4 : `PdfDocument` (encapsulation react-pdf)

**Files:**
- Create: `frontend/src/components/invoice/PdfDocument.tsx`

**Interfaces:**
- Consumes: `pdfWorker.ts` (Task 1) pour `Document`/`Page`.
- Produces:
```ts
export interface PdfDocumentProps {
  url: string
  zoom: number
  rotation: number
  pageNumber: number
  onLoadSuccess: (numPages: number) => void
  onLoadError: () => void
}
export function PdfDocument(props: PdfDocumentProps): JSX.Element
```

- [ ] **Step 1: Implémenter le composant**

`frontend/src/components/invoice/PdfDocument.tsx` :
```tsx
import { Document, Page } from './pdfWorker'

export interface PdfDocumentProps {
  url: string
  zoom: number
  rotation: number
  pageNumber: number
  onLoadSuccess: (numPages: number) => void
  onLoadError: () => void
}

export function PdfDocument({ url, zoom, rotation, pageNumber, onLoadSuccess, onLoadError }: PdfDocumentProps) {
  return (
    <Document
      file={url}
      onLoadSuccess={(pdf: { numPages: number }) => onLoadSuccess(pdf.numPages)}
      onLoadError={onLoadError}
      loading={<div className="text-sm text-gray-500 p-8">…</div>}
    >
      <Page pageNumber={pageNumber} scale={zoom} rotate={rotation} />
    </Document>
  )
}
```
Note : pas de test unitaire dédié ici — `PdfDocument` n'est qu'un adaptateur ; il est couvert indirectement via le mock react-pdf dans les tests du conteneur (Task 5).

- [ ] **Step 2: Vérifier la compilation**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx tsc --noEmit
```
Attendu : 0 erreur.

- [ ] **Step 3: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/components/invoice/PdfDocument.tsx
git commit -F - <<'EOF'
feat(c3): composant PdfDocument encapsulant react-pdf (scale+rotate)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 5 : Conteneur `DocumentViewerModal` (état + câblage) — TDD

**Files:**
- Modify: `frontend/src/components/invoice/DocumentViewerModal.tsx` (réécriture complète)
- Test: `frontend/src/test/components/DocumentViewerModal.test.tsx` (ajout bloc `DocumentViewerModal`)

**Interfaces:**
- Consumes: `ViewerToolbar` (Task 3), `PdfDocument` (Task 4), clés i18n (Task 2).
- Produces: signature publique inchangée `{ url, filename, fileType?, onClose }`.

- [ ] **Step 1: Ajouter les tests RED (mock react-pdf)**

Ajouter en haut de `frontend/src/test/components/DocumentViewerModal.test.tsx`, AVANT les imports de composants, le mock du module worker (qui ré-exporte react-pdf) :
```tsx
vi.mock('@/components/invoice/pdfWorker', () => ({
  pdfjs: { GlobalWorkerOptions: {} },
  Document: ({ children, onLoadSuccess }: any) => {
    // simule un PDF de 3 pages
    onLoadSuccess?.({ numPages: 3 })
    return <div data-testid="pdf-doc">{children}</div>
  },
  Page: ({ scale, rotate, pageNumber }: any) => (
    <div data-testid="pdf-page" data-scale={scale} data-rotate={rotate} data-page={pageNumber} />
  ),
}))
```
Puis ajouter le bloc de tests du conteneur :
```tsx
import { DocumentViewerModal } from '@/components/invoice/DocumentViewerModal'

function renderModal(props = {}) {
  const onClose = vi.fn()
  render(
    <I18nextProvider i18n={i18n}>
      <DocumentViewerModal url="http://x/doc.pdf" filename="doc.pdf" fileType="application/pdf" onClose={onClose} {...props} />
    </I18nextProvider>
  )
  return { onClose }
}

describe('DocumentViewerModal', () => {
  it('rend un PDF via react-pdf à 100% / 0°', () => {
    renderModal()
    const page = screen.getByTestId('pdf-page')
    expect(page.getAttribute('data-scale')).toBe('1')
    expect(page.getAttribute('data-rotate')).toBe('0')
  })

  it('zoom + augmente le scale, zoom - le diminue', async () => {
    const u = userEvent.setup()
    renderModal()
    await u.click(screen.getByLabelText(/zoom avant/i))
    expect(Number(screen.getByTestId('pdf-page').getAttribute('data-scale'))).toBeGreaterThan(1)
    await u.click(screen.getByLabelText(/zoom arrière/i))
    expect(Number(screen.getByTestId('pdf-page').getAttribute('data-scale'))).toBe(1)
  })

  it('rotation cycle 90/180/270/0', async () => {
    const u = userEvent.setup()
    renderModal()
    const rot = () => screen.getByTestId('pdf-page').getAttribute('data-rotate')
    await u.click(screen.getByLabelText(/pivoter/i)); expect(rot()).toBe('90')
    await u.click(screen.getByLabelText(/pivoter/i)); expect(rot()).toBe('180')
    await u.click(screen.getByLabelText(/pivoter/i)); expect(rot()).toBe('270')
    await u.click(screen.getByLabelText(/pivoter/i)); expect(rot()).toBe('0')
  })

  it('reset ramène à 100% / 0°', async () => {
    const u = userEvent.setup()
    renderModal()
    await u.click(screen.getByLabelText(/zoom avant/i))
    await u.click(screen.getByLabelText(/pivoter/i))
    await u.click(screen.getByLabelText(/réinitialiser/i))
    const page = screen.getByTestId('pdf-page')
    expect(page.getAttribute('data-scale')).toBe('1')
    expect(page.getAttribute('data-rotate')).toBe('0')
  })

  it('rend une image avec transform zoom+rotation', async () => {
    const u = userEvent.setup()
    render(
      <I18nextProvider i18n={i18n}>
        <DocumentViewerModal url="http://x/p.png" filename="p.png" fileType="image/png" onClose={vi.fn()} />
      </I18nextProvider>
    )
    await u.click(screen.getByLabelText(/zoom avant/i))
    const img = screen.getByRole('img')
    expect(img.getAttribute('style')).toMatch(/scale/)
    await u.click(screen.getByLabelText(/pivoter/i))
    expect(img.getAttribute('style')).toMatch(/rotate/)
  })

  it('affiche un lien download pour un type non prévisualisable', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <DocumentViewerModal url="http://x/f.zip" filename="f.zip" fileType="application/zip" onClose={vi.fn()} />
      </I18nextProvider>
    )
    expect(screen.getByText(/aperçu non disponible/i)).toBeDefined()
  })
})
```

- [ ] **Step 2: Lancer → échec attendu**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx vitest run src/test/components/DocumentViewerModal.test.tsx
```
Attendu : FAIL (le conteneur ne pilote pas encore zoom/rotation, pas de toolbar).

- [ ] **Step 3: Réécrire le conteneur**

Remplacer intégralement `frontend/src/components/invoice/DocumentViewerModal.tsx` :
```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download } from 'lucide-react'
import { ViewerToolbar } from './ViewerToolbar'
import { PdfDocument } from './PdfDocument'

interface DocumentViewerModalProps {
  url: string
  filename: string
  fileType?: string
  onClose: () => void
}

const ZOOM_MIN = 0.5
const ZOOM_MAX = 3.0
const ZOOM_STEP = 0.25

/**
 * M9 — visualiseur de documents in-app avec contrôles zoom/rotation/reset.
 * PDF rendus via react-pdf (scale + rotate), images via transform CSS.
 * Les types non prévisualisables retombent sur un lien de téléchargement.
 */
export function DocumentViewerModal({ url, filename, fileType, onClose }: DocumentViewerModalProps) {
  const { t } = useTranslation()
  const isPdf = (fileType ?? '').includes('pdf') || filename.toLowerCase().endsWith('.pdf')
  const isImage = (fileType ?? '').startsWith('image/')
    || /\.(png|jpe?g|gif|tiff?|webp)$/i.test(filename)

  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [pageNumber, setPageNumber] = useState(1)
  const [numPages, setNumPages] = useState(0)
  const [pdfError, setPdfError] = useState(false)

  const zoomIn = () => setZoom(z => Math.min(ZOOM_MAX, +(z + ZOOM_STEP).toFixed(2)))
  const zoomOut = () => setZoom(z => Math.max(ZOOM_MIN, +(z - ZOOM_STEP).toFixed(2)))
  const rotate = () => setRotation(r => (r + 90) % 360)
  const reset = () => { setZoom(1); setRotation(0) }

  const previewable = isPdf || isImage

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <h2 className="text-sm font-semibold text-gray-800 truncate">{filename}</h2>
          <div className="flex items-center gap-3">
            <a href={url} download className="text-gray-500 hover:text-primary" title={t('app.download', 'Télécharger')}>
              <Download className="w-4 h-4" />
            </a>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
          </div>
        </div>

        {previewable && !pdfError && (
          <ViewerToolbar
            zoom={zoom}
            rotation={rotation}
            canZoomIn={zoom < ZOOM_MAX}
            canZoomOut={zoom > ZOOM_MIN}
            pageNumber={isPdf ? pageNumber : undefined}
            numPages={isPdf ? numPages : undefined}
            onZoomIn={zoomIn}
            onZoomOut={zoomOut}
            onRotate={rotate}
            onReset={reset}
            onPrevPage={() => setPageNumber(p => Math.max(1, p - 1))}
            onNextPage={() => setPageNumber(p => Math.min(numPages, p + 1))}
          />
        )}

        <div className="flex-1 overflow-auto bg-gray-100 flex items-center justify-center">
          {isPdf && !pdfError ? (
            <PdfDocument
              url={url}
              zoom={zoom}
              rotation={rotation}
              pageNumber={pageNumber}
              onLoadSuccess={setNumPages}
              onLoadError={() => setPdfError(true)}
            />
          ) : isImage ? (
            <img
              src={url}
              alt={filename}
              className="max-w-full max-h-full object-contain"
              style={{ transform: `scale(${zoom}) rotate(${rotation}deg)` }}
            />
          ) : (
            <div className="text-center text-sm text-gray-500 p-8">
              <p>{pdfError ? t('invoice.viewer.loadError', 'Impossible de charger le document.') : t('invoice.viewer.noPreview', 'Aperçu non disponible pour ce type de fichier.')}</p>
              <a href={url} download className="inline-flex items-center gap-1.5 mt-3 text-primary hover:underline">
                <Download className="w-4 h-4" /> {t('app.download', 'Télécharger')}
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Lancer → succès attendu**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx vitest run src/test/components/DocumentViewerModal.test.tsx
```
Attendu : PASS (tous les blocs).

- [ ] **Step 5: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/components/invoice/DocumentViewerModal.tsx frontend/src/test/components/DocumentViewerModal.test.tsx
git commit -F - <<'EOF'
feat(c3): zoom/rotate/reset dans le visualiseur de documents (M9 #4)

Conteneur DocumentViewerModal : état zoom/rotation/page, câblage toolbar +
PdfDocument (PDF) et transform CSS (images), fallback download conservé.
Signature publique inchangée.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 6 : Vérif globale + bascule conformité

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (ligne M9 #4, ~294)

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: gap M9 #4 levé.

- [ ] **Step 1: Suite frontend complète**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend"
npx tsc --noEmit && npx vitest run && npm run build
```
Attendu : tsc 0 erreur, vitest tout vert, build vite OK.
Si un bug réel surgit → le corriger, puis le loguer dans `docs/KNOWN_ISSUES_REGISTRY.md` AVANT de committer le fix.

- [ ] **Step 2: Basculer la matrice de conformité**

Dans `docs/COMPLIANCE_MATRIX.md`, ligne 294, passer le statut de `🟠` à `✅` et remplacer la note par : `DocumentViewerModal : contrôles zoom/rotation/reset (react-pdf pour PDF, transform CSS pour images) + pagination PDF.` Mettre à jour la ligne « Gaps M9 » (317) pour retirer « #4 pas de zoom/rotate dédiés ». Ajuster le compteur M9 (518) si présent.

- [ ] **Step 3: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add docs/COMPLIANCE_MATRIX.md
git commit -F - <<'EOF'
docs(c3): M9 #4 ✅ — zoom/rotate du visualiseur livré

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Self-Review (effectuée à la rédaction)

- **Couverture spec :** §3 architecture → Tasks 1/3/4/5 ; §4 état/bornes → Task 5 ; §5 flux → Task 5 ; §6 fallback → Task 5 (branche pdfError + noPreview) ; §7 tests → Tasks 3 & 5 ; §8 i18n → Task 2 ; §10 critères → Task 6. Aucun trou.
- **Placeholders :** aucun TBD/TODO ; code complet à chaque étape.
- **Cohérence des types :** `ViewerToolbarProps` et `PdfDocumentProps` identiques entre définition (Tasks 3/4) et consommation (Task 5) ; `onLoadSuccess(numPages:number)` ↔ `setNumPages` ; mock react-pdf cohérent avec `PdfDocument` (props `scale`/`rotate`/`pageNumber`).
- **Point de vigilance d'exécution :** version exacte de react-pdf compatible React 19 et nom réel du fichier worker (Task 1) — à valider à l'install, fallback documenté.
