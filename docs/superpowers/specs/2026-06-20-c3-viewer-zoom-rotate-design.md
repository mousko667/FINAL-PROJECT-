# C3 — Contrôles zoom/rotate du visualiseur de documents (M9 #4)

**Date :** 2026-06-20
**Lot :** Section C, familles B+C — item C3
**Réf. exigence :** M9 #4 « Document viewer with zoom/rotate » (gap 🟠 dans `docs/COMPLIANCE_MATRIX.md:294`)
**Type :** Frontend pur. Pas de backend, pas de migration Flyway.

---

## 1. Objectif

Le visualiseur de documents actuel (`frontend/src/components/invoice/DocumentViewerModal.tsx`)
affiche les PDF dans une `<iframe>` (zoom natif du navigateur uniquement, aucun contrôle
applicatif) et les images dans une `<img>` sans contrôle. L'exigence M9 #4 demande des
contrôles **zoom et rotation** explicites.

Une `<iframe>` est une boîte noire : on ne peut pas lui appliquer zoom ni rotation
programmatiquement. Pour des contrôles réels sur les PDF, on remplace l'iframe par un rendu
`react-pdf` (pdf.js). Les images sont gérées via `transform` CSS.

La **signature publique** du composant reste inchangée
(`url`, `filename`, `fileType`, `onClose`) → aucun appelant modifié. Seul appelant actuel :
`frontend/src/pages/InvoiceDetailPage.tsx:512`.

---

## 2. Décisions de cadrage (validées)

| Sujet | Décision |
|-------|----------|
| Portée PDF | Images **+ PDF** via `react-pdf` (pdf.js). Couvre pleinement M9 #4. |
| Contrôles | Zoom −/+ (avec indicateur %), rotation 90°, **reset**, + navigation pages pour PDF multipages. |
| Tests | vitest avec `react-pdf` **mocké** (worker/canvas ne tournent pas sous jsdom). TDD RED→GREEN sur la logique UI. |

---

## 3. Architecture des composants

La modale monolithique actuelle est découpée en 4 unités à responsabilité unique :

1. **`DocumentViewerModal`** (conteneur) — overlay plein écran, en-tête (titre + bouton
   download + close), détient l'état (`zoom`, `rotation`, `pageNumber`, `numPages`), monte
   la `ViewerToolbar` et la zone de rendu (PDF / image / fallback download).

2. **`ViewerToolbar`** (présentationnel, sans logique métier) — boutons zoom −/+, indicateur
   `%`, rotation 90°, reset. Navigation pages (préc./suiv. + « page X / N ») affichée
   **seulement si `numPages > 1`**. Reçoit l'état et les callbacks en props.

3. **`PdfDocument`** — encapsule `<Document>` / `<Page>` de react-pdf, applique
   `scale={zoom}` et `rotate={rotation}`, expose `onLoadSuccess({numPages})` et
   `onLoadError`. Gère loading et erreur.

4. **Branche image** — `<img>` dans un conteneur `overflow-auto`, avec
   `style={{ transform: scale(zoom) rotate(rotation deg) }}`.

**Worker pdf.js** : configuré une fois via `pdfjs.GlobalWorkerOptions.workerSrc`, dans un
petit module dédié (`pdfWorker.ts`) importé par `PdfDocument`, en utilisant l'import d'URL
Vite (`new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url)` ou suffixe `?url`).

> Note compatibilité : projet en **React 19.2 + Vite 8**. La version de `react-pdf` /
> `pdfjs-dist` retenue doit supporter React 19 (react-pdf ≥ 9.x). À fixer au moment de
> l'installation.

---

## 4. État & comportement

- **`zoom`** : `number`, défaut `1.0`. Pas de **0.25**. Bornes **0.5 → 3.0**. Indicateur
  affiché en pourcentage (`Math.round(zoom * 100)`). Boutons − / + désactivés aux bornes.
- **`rotation`** : `0 | 90 | 180 | 270`. Rotation horaire : `(rotation + 90) % 360`.
- **`reset`** : `zoom = 1`, `rotation = 0`. Ne touche **pas** la page courante.
- **PDF multipages** : `pageNumber` (défaut `1`), navigation préc./suiv. bornée
  `[1, numPages]`. `numPages` provient de `onLoadSuccess`.
- **Changement de document** (nouveau `url`) → réinitialise tout l'état (via `key` sur le
  composant ou `useEffect` sur `url`).

---

## 5. Flux de données

Unidirectionnel. Le conteneur `DocumentViewerModal` détient l'état et le passe :
- à `ViewerToolbar` → valeurs affichées + callbacks qui déclenchent les mutations d'état ;
- à la zone de rendu (`PdfDocument` ou `<img>`) → **consomme** `zoom` / `rotation` /
  `pageNumber`.

Aucun état dupliqué entre les unités.

---

## 6. Gestion d'erreur / fallback

- PDF échoue au chargement (`onLoadError`) → message i18n + lien download
  (réutilise le pattern « noPreview » existant).
- Type ni PDF ni image → branche download inchangée (telle qu'aujourd'hui).
- Worker pdf.js absent / cassé → capté par `onLoadError` → même fallback.

---

## 7. Tests (`DocumentViewerModal.test.tsx`, vitest)

`react-pdf` est **mocké** : `<Document>` invoque `onLoadSuccess({ numPages })`, `<Page>`
rend un stub exposant `scale` / `rotate` pour assertion.

Cas couverts :
- Zoom **+** → `%` augmente ; **−** → diminue.
- Bornes : à 50 % le bouton − est désactivé ; à 300 % le bouton + est désactivé.
- Rotation → cycle 90 → 180 → 270 → 0.
- Reset → retour 100 % / 0°.
- Image : l'attribut `transform` reflète zoom **et** rotation.
- PDF multipages : navigation préc./suiv., bornes respectées ; toolbar pages **masquée**
  si `numPages === 1`.
- Fallback : type inconnu → lien download ; `onLoadError` → message d'erreur i18n.

TDD : écrire les tests RED d'abord, puis l'implémentation GREEN.

---

## 8. i18n

Fichiers **frontend** : `frontend/src/i18n/fr.json` et `en.json` (UTF-8 — **pas** le fichier
backend ISO-8859-1). Le bloc `invoice.viewer` existe déjà
(`fr.json:278`). Clés ajoutées :

```
invoice.viewer.zoomIn
invoice.viewer.zoomOut
invoice.viewer.rotate
invoice.viewer.resetView
invoice.viewer.page       (ex. "Page")
invoice.viewer.of         (ex. "sur" / "of")
invoice.viewer.loadError  (échec de chargement du PDF)
```

Parité fr/en obligatoire (vérif. parité i18n du projet).

---

## 9. Hors périmètre (YAGNI)

Pan / glisser, molette + Ctrl pour zoomer, ajuster-à-la-largeur, miniatures de pages,
recherche de texte dans le PDF.

---

## 10. Critères d'acceptation

- [ ] `react-pdf` (+ `pdfjs-dist`) installé, compatible React 19, worker configuré pour Vite.
- [ ] Zoom −/+, rotation 90°, reset fonctionnels sur PDF **et** images.
- [ ] PDF multipages navigables ; toolbar pages masquée en monopage.
- [ ] Fallback download intact pour les types non prévisualisables et en cas d'erreur PDF.
- [ ] Signature publique du composant inchangée ; `InvoiceDetailPage.tsx` non modifié.
- [ ] Clés i18n fr + en ajoutées, parité OK.
- [ ] `DocumentViewerModal.test.tsx` vert (react-pdf mocké).
- [ ] `npx tsc --noEmit` vert, `npm run build` (vite) vert, `vitest run` vert.
- [ ] Item C3 basculé ✅ dans `docs/COMPLIANCE_MATRIX.md` (gap M9 #4 levé).
- [ ] Tout bug réel rencontré logué dans `docs/KNOWN_ISSUES_REGISTRY.md` avant commit.
