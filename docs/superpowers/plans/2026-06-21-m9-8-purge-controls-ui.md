# M9 #8 — Contrôles de purge en UI (ADMIN) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une page admin frontend qui liste les documents de facture périmés encore en disposition PENDING et permet de les marquer RETAINED ou PURGED, fermant COMPLIANCE_MATRIX L298 (M9 UI #8).

**Architecture:** Page React lazy-loadée sous `PageRoleGuard ROLE_ADMIN`, consommant deux endpoints backend déjà livrés (`GET /retention/pending-documents`, `PUT /retention/documents/{id}/disposition`). Aucun changement backend, aucune migration Flyway. Le tableau se vide naturellement au re-fetch après chaque disposition (le backend ne liste que les PENDING périmés). « Conserver » s'applique directement ; « Purger » passe par une modale de confirmation locale.

**Tech Stack:** React 18 + TypeScript, @tanstack/react-query (useQuery/useMutation/useQueryClient), react-i18next, axios via `@/services/apiClient`, lucide-react, Tailwind. Tests : vitest + @testing-library/react.

## Global Constraints

- **Zéro backend, zéro Flyway** : endpoints, DTO, audit et clés i18n backend existent déjà. Ne modifier aucun fichier `src/main` ni `src/main/resources/i18n/messages_*.properties`.
- `apiClient` a déjà `baseURL = /api/v1` → appeler `/retention/...` SANS préfixe `/api/v1` (PROB-038, double-préfixe interdit).
- Tout texte utilisateur passe par `t('clé', 'fallback FR')` ; clés ajoutées en **parité stricte** dans `frontend/src/i18n/fr.json` ET `frontend/src/i18n/en.json`.
- Messages d'erreur backend potentiellement renvoyés comme clés i18n → afficher via `t(...)` (PROB-006).
- SoD : page ADMIN only via `PageRoleGuard allowedRoles={['ROLE_ADMIN']}` (PROB-065). Backend renvoie déjà 403 aux autres rôles.
- Critère de fin global : `tsc --noEmit` 0 erreur, `vitest` tout vert, backend inchangé (464/0/0 si relancé). Commits atomiques par tâche. Push hors périmètre de ce plan (géré à la cadence seuil-10 / fin de lot).
- Commandes frontend : exécuter depuis `frontend/` (`npm run`, `npx tsc`, `npx vitest`).

## File Structure

- **Create** `frontend/src/pages/admin/AdminRetentionDispositionPage.tsx` — la page (liste + actions + modale purge). Responsabilité unique : disposition des documents périmés.
- **Create** `frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx` — tests vitest de la page.
- **Modify** `frontend/src/i18n/fr.json` — ajouter bloc `retentionDisposition` (après `archiveCompliance`).
- **Modify** `frontend/src/i18n/en.json` — ajouter bloc `retentionDisposition` (parité).
- **Modify** `frontend/src/AppRoutes.tsx` — déclaration lazy + `<Route>`.
- **Modify** `frontend/src/components/layout/Sidebar.tsx` — import icône `Trash2` + `NavItem`.

---

### Task 1 : i18n — bloc `retentionDisposition` (FR + EN)

**Files:**
- Modify: `frontend/src/i18n/fr.json` (bloc `archiveCompliance` se termine vers L1111 par `}` ; ajouter une virgule puis le nouveau bloc avant le `}` racine final)
- Modify: `frontend/src/i18n/en.json` (idem, bloc `archiveCompliance` se termine vers L1111)

**Interfaces:**
- Consumes: rien.
- Produces: clés i18n `retentionDisposition.*` utilisées par Task 2, 3, 4 :
  `navTitle, title, subtitle, note, colFile, colInvoice, colUploaded, colActions, retain, purge, empty, emptyHint, confirmPurgeTitle, confirmPurgeBody, confirm, cancel, loadError, actionError`.

- [ ] **Step 1 : Ajouter le bloc FR**

Dans `frontend/src/i18n/fr.json`, le bloc `"archiveCompliance": { ... }` est le dernier avant la `}` de fin de fichier. Ajouter une virgule après sa `}` de fermeture, puis insérer :

```json
  "retentionDisposition": {
    "navTitle": "Purge",
    "title": "Contrôles de purge",
    "subtitle": "Documents de facture ayant dépassé la durée de rétention et en attente de décision.",
    "note": "« Purger » est un marquage de conformité : le fichier n'est pas supprimé physiquement du stockage. La décision est tracée dans l'audit.",
    "colFile": "Fichier",
    "colInvoice": "Facture",
    "colUploaded": "Déposé le",
    "colActions": "Actions",
    "retain": "Conserver",
    "purge": "Purger",
    "empty": "Aucun document à traiter.",
    "emptyHint": "Tous les documents archivés sont dans leur durée de rétention.",
    "confirmPurgeTitle": "Marquer ce document comme purgé ?",
    "confirmPurgeBody": "Marquage de conformité ; le fichier n'est pas supprimé physiquement du stockage. Cette action est tracée dans l'audit.",
    "confirm": "Confirmer",
    "cancel": "Annuler",
    "loadError": "Échec du chargement des documents à traiter.",
    "actionError": "Échec de la mise à jour de la disposition."
  }
```

- [ ] **Step 2 : Ajouter le bloc EN (parité)**

Dans `frontend/src/i18n/en.json`, même emplacement (après `archiveCompliance`), virgule puis :

```json
  "retentionDisposition": {
    "navTitle": "Purge",
    "title": "Purge controls",
    "subtitle": "Invoice documents past the retention period awaiting a decision.",
    "note": "\"Purge\" is a compliance marking: the file is not physically deleted from storage. The decision is recorded in the audit log.",
    "colFile": "File",
    "colInvoice": "Invoice",
    "colUploaded": "Uploaded",
    "colActions": "Actions",
    "retain": "Retain",
    "purge": "Purge",
    "empty": "No documents to process.",
    "emptyHint": "All archived documents are within their retention period.",
    "confirmPurgeTitle": "Mark this document as purged?",
    "confirmPurgeBody": "Compliance marking; the file is not physically deleted from storage. This action is recorded in the audit log.",
    "confirm": "Confirm",
    "cancel": "Cancel",
    "loadError": "Failed to load documents to process.",
    "actionError": "Failed to update the disposition."
  }
```

- [ ] **Step 3 : Vérifier que les deux JSON sont valides et à parité**

Run (depuis `frontend/`) :
```bash
node -e "const f=require('./src/i18n/fr.json').retentionDisposition,e=require('./src/i18n/en.json').retentionDisposition;const a=Object.keys(f).sort(),b=Object.keys(e).sort();if(JSON.stringify(a)!==JSON.stringify(b)){console.error('PARITE KO',a,b);process.exit(1)}console.log('OK',a.length,'cles')"
```
Expected : `OK 18 cles` (sortie sans erreur ; JSON parsé sans exception = fichiers valides).

- [ ] **Step 4 : Commit**

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(m9-8): i18n keys for purge controls page (FR+EN parity)"
```

---

### Task 2 : Page `AdminRetentionDispositionPage` — liste + état vide

**Files:**
- Create: `frontend/src/pages/admin/AdminRetentionDispositionPage.tsx`
- Test: `frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx`

**Interfaces:**
- Consumes: clés i18n `retentionDisposition.*` (Task 1). Endpoint `GET /retention/pending-documents` → `ApiResponse<RetentionPendingDocumentDTO[]>` où `RetentionPendingDocumentDTO = { id: string; invoiceId: string | null; originalFilename: string; uploadedAt: string; retentionDisposition: 'PENDING' | 'RETAINED' | 'PURGED' }`. La réponse axios a la forme `{ data: { data: [...] } }`.
- Produces: composant `default export AdminRetentionDispositionPage` (consommé par Task 4). Type local `PendingDocument` (même forme que le DTO).

- [ ] **Step 1 : Écrire le test (liste peuplée + état vide + accès refusé)**

Créer `frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx`. Calqué sur `AdminArchiveCompliancePage.test.tsx` (Provider Redux + QueryClient + MemoryRouter + I18nextProvider). Le mock `apiClient` expose `get` ET `put` (utilisés en Task 3).

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AdminRetentionDispositionPage from '@/pages/admin/AdminRetentionDispositionPage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn(), put: vi.fn() },
}))

const adminUser: AuthUser = { id: '1', username: 'admin', email: 'admin@oct.fr', roles: ['ROLE_ADMIN'] }

const makeStore = (user: AuthUser | null) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user, accessToken: 'test-token', refreshToken: null, isAuthenticated: !!user },
    },
  })

function renderPage(user: AuthUser | null = adminUser) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={makeStore(user)}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <AdminRetentionDispositionPage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const sampleDocs = [
  { id: 'd1', invoiceId: 'inv1', originalFilename: 'facture-2014.pdf', uploadedAt: '2014-03-01T10:00:00Z', retentionDisposition: 'PENDING' },
]

describe('AdminRetentionDispositionPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders rows when there are expired pending documents', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleDocs } })
    renderPage()
    expect(await screen.findByText('facture-2014.pdf')).toBeInTheDocument()
  })

  it('renders the reassuring empty state when there is nothing to process', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [] } })
    renderPage()
    expect(await screen.findByText('Aucun document à traiter.')).toBeInTheDocument()
    expect(screen.queryByText('facture-2014.pdf')).toBeNull()
  })

  it('denies access to non-admin users', () => {
    renderPage({ id: '2', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] })
    expect(screen.queryByText('facture-2014.pdf')).toBeNull()
  })
})
```

- [ ] **Step 2 : Lancer le test → échec attendu**

Run (depuis `frontend/`) :
```bash
npx vitest run src/test/pages/AdminRetentionDispositionPage.test.tsx
```
Expected : FAIL — module `@/pages/admin/AdminRetentionDispositionPage` introuvable.

- [ ] **Step 3 : Créer la page (liste + état vide ; actions inertes pour l'instant)**

Créer `frontend/src/pages/admin/AdminRetentionDispositionPage.tsx`. À ce stade les boutons existent mais ne déclenchent encore aucune mutation (câblées en Task 3) — on garde le test de liste/vide/accès vert.

```tsx
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Link } from 'react-router-dom'
import { Loader2, CheckCircle, Trash2, ShieldCheck } from 'lucide-react'

interface PendingDocument {
  id: string
  invoiceId: string | null
  originalFilename: string
  uploadedAt: string
  retentionDisposition: 'PENDING' | 'RETAINED' | 'PURGED'
}

export default function AdminRetentionDispositionPage() {
  const { t, i18n } = useTranslation()

  const { data: docs, isLoading, isError } = useQuery({
    queryKey: ['retention-pending-documents'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PendingDocument[] }>('/retention/pending-documents')
      return data.data
    },
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('retentionDisposition.title', 'Contrôles de purge')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('retentionDisposition.subtitle', 'Documents de facture ayant dépassé la durée de rétention et en attente de décision.')}
          </p>
        </div>

        <p className="text-xs text-gray-500 bg-gray-50 border rounded-lg p-3">
          {t('retentionDisposition.note', '« Purger » est un marquage de conformité : le fichier n’est pas supprimé physiquement du stockage. La décision est tracée dans l’audit.')}
        </p>

        {isLoading ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : isError ? (
          <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
            {t('retentionDisposition.loadError', 'Échec du chargement des documents à traiter.')}
          </p>
        ) : !docs || docs.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
            <CheckCircle className="w-10 h-10 text-green-500" />
            <p className="text-sm font-medium text-gray-800">{t('retentionDisposition.empty', 'Aucun document à traiter.')}</p>
            <p className="text-xs text-gray-500">{t('retentionDisposition.emptyHint', 'Tous les documents archivés sont dans leur durée de rétention.')}</p>
          </div>
        ) : (
          <div className="bg-white rounded-xl border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs text-gray-500">
                <tr>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colFile', 'Fichier')}</th>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colInvoice', 'Facture')}</th>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colUploaded', 'Déposé le')}</th>
                  <th className="px-4 py-2 font-medium text-right">{t('retentionDisposition.colActions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody>
                {docs.map(doc => (
                  <tr key={doc.id} className="border-t">
                    <td className="px-4 py-2 text-gray-800">{doc.originalFilename}</td>
                    <td className="px-4 py-2">
                      {doc.invoiceId
                        ? <Link to={`/invoices/${doc.invoiceId}`} className="text-primary hover:underline">{doc.invoiceId.slice(0, 8)}</Link>
                        : <span className="text-gray-400">—</span>}
                    </td>
                    <td className="px-4 py-2 text-gray-600">{new Date(doc.uploadedAt).toLocaleString(i18n.language)}</td>
                    <td className="px-4 py-2">
                      <div className="flex items-center justify-end gap-2">
                        <button className="px-3 py-1 text-xs rounded-md border hover:bg-gray-50">
                          {t('retentionDisposition.retain', 'Conserver')}
                        </button>
                        <button className="flex items-center gap-1 px-3 py-1 text-xs rounded-md bg-red-600 text-white hover:bg-red-700">
                          <Trash2 className="w-3.5 h-3.5" />{t('retentionDisposition.purge', 'Purger')}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <p className="flex items-center gap-1.5 text-xs text-gray-400">
          <ShieldCheck className="w-3.5 h-3.5" />
          {t('retentionDisposition.note', '« Purger » est un marquage de conformité ; aucune suppression physique.')}
        </p>
      </div>
    </PageRoleGuard>
  )
}
```

- [ ] **Step 4 : Lancer le test → vert**

Run (depuis `frontend/`) :
```bash
npx vitest run src/test/pages/AdminRetentionDispositionPage.test.tsx
```
Expected : PASS (3 tests).

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/pages/admin/AdminRetentionDispositionPage.tsx frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx
git commit -m "feat(m9-8): retention disposition page — list + empty state"
```

---

### Task 3 : Actions — Conserver (direct) + Purger (modale de confirmation)

**Files:**
- Modify: `frontend/src/pages/admin/AdminRetentionDispositionPage.tsx`
- Test: `frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx` (ajout de 2 tests)

**Interfaces:**
- Consumes: endpoint `PUT /retention/documents/{id}/disposition` body `{ disposition: 'RETAINED' | 'PURGED' }`. `apiClient.put` est déjà mocké (Task 2).
- Produces: rien de nouveau pour les tâches suivantes.

- [ ] **Step 1 : Ajouter les tests d'action (Conserver direct ; Purger via modale)**

Ajouter dans le `describe` existant de `AdminRetentionDispositionPage.test.tsx`. Importer `fireEvent` (compléter l'import existant : `import { render, screen, fireEvent } from '@testing-library/react'`).

```tsx
  it('retains a document directly via PUT RETAINED', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: sampleDocs } })
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: { ...sampleDocs[0], retentionDisposition: 'RETAINED' } } })
    renderPage()
    fireEvent.click(await screen.findByText('Conserver'))
    expect(apiClient.put).toHaveBeenCalledWith('/retention/documents/d1/disposition', { disposition: 'RETAINED' })
  })

  it('purges only after confirming the modal', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: sampleDocs } })
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: { ...sampleDocs[0], retentionDisposition: 'PURGED' } } })
    renderPage()
    fireEvent.click(await screen.findByText('Purger'))
    // modale ouverte, aucun PUT encore
    expect(apiClient.put).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByText('Confirmer'))
    expect(apiClient.put).toHaveBeenCalledWith('/retention/documents/d1/disposition', { disposition: 'PURGED' })
  })
```

Note : `apiClient.get` passe de `mockResolvedValueOnce` à `mockResolvedValue` (re-fetch après invalidation possible).

- [ ] **Step 2 : Lancer les nouveaux tests → échec attendu**

Run (depuis `frontend/`) :
```bash
npx vitest run src/test/pages/AdminRetentionDispositionPage.test.tsx
```
Expected : FAIL sur les 2 nouveaux tests (les boutons n'appellent pas encore `apiClient.put` ; pas de bouton « Confirmer »).

- [ ] **Step 3 : Câbler la mutation + la modale**

Modifier `AdminRetentionDispositionPage.tsx`. Mettre à jour les imports en tête :
```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
```

Dans le composant, après le `useQuery`, ajouter état + mutation :
```tsx
  const queryClient = useQueryClient()
  const [purgeTarget, setPurgeTarget] = useState<PendingDocument | null>(null)

  const disposition = useMutation({
    mutationFn: ({ id, value }: { id: string; value: 'RETAINED' | 'PURGED' }) =>
      apiClient.put(`/retention/documents/${id}/disposition`, { disposition: value }),
    onSuccess: () => {
      setPurgeTarget(null)
      queryClient.invalidateQueries({ queryKey: ['retention-pending-documents'] })
    },
  })
```

Remplacer les deux `<button>` inertes de la cellule Actions par :
```tsx
                        <button
                          onClick={() => disposition.mutate({ id: doc.id, value: 'RETAINED' })}
                          disabled={disposition.isPending}
                          className="px-3 py-1 text-xs rounded-md border hover:bg-gray-50 disabled:opacity-60">
                          {t('retentionDisposition.retain', 'Conserver')}
                        </button>
                        <button
                          onClick={() => setPurgeTarget(doc)}
                          disabled={disposition.isPending}
                          className="flex items-center gap-1 px-3 py-1 text-xs rounded-md bg-red-600 text-white hover:bg-red-700 disabled:opacity-60">
                          <Trash2 className="w-3.5 h-3.5" />{t('retentionDisposition.purge', 'Purger')}
                        </button>
```

Ajouter le message d'erreur d'action et la modale juste avant le `<p>` final `ShieldCheck` (toujours dans le `<div className="max-w-4xl ...">`) :
```tsx
        {disposition.isError && (
          <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
            {t('retentionDisposition.actionError', 'Échec de la mise à jour de la disposition.')}
          </p>
        )}

        {purgeTarget && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
            <div className="bg-white rounded-xl shadow-lg max-w-md w-full p-6 space-y-4">
              <h2 className="text-lg font-semibold text-gray-900">
                {t('retentionDisposition.confirmPurgeTitle', 'Marquer ce document comme purgé ?')}
              </h2>
              <p className="text-sm text-gray-600">
                {t('retentionDisposition.confirmPurgeBody', 'Marquage de conformité ; le fichier n’est pas supprimé physiquement du stockage. Cette action est tracée dans l’audit.')}
              </p>
              <p className="text-sm font-medium text-gray-800">{purgeTarget.originalFilename}</p>
              <div className="flex items-center justify-end gap-2 pt-2 border-t">
                <button
                  onClick={() => setPurgeTarget(null)}
                  className="px-4 py-2 text-sm rounded-lg border hover:bg-gray-50">
                  {t('retentionDisposition.cancel', 'Annuler')}
                </button>
                <button
                  onClick={() => disposition.mutate({ id: purgeTarget.id, value: 'PURGED' })}
                  disabled={disposition.isPending}
                  className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 disabled:opacity-60">
                  {t('retentionDisposition.confirm', 'Confirmer')}
                </button>
              </div>
            </div>
          </div>
        )}
```

- [ ] **Step 4 : Lancer toute la suite de la page → verte**

Run (depuis `frontend/`) :
```bash
npx vitest run src/test/pages/AdminRetentionDispositionPage.test.tsx
```
Expected : PASS (5 tests).

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/pages/admin/AdminRetentionDispositionPage.tsx frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx
git commit -m "feat(m9-8): retain (direct) + purge (confirm modal) actions"
```

---

### Task 4 : Câblage route + entrée sidebar

**Files:**
- Modify: `frontend/src/AppRoutes.tsx` (déclaration lazy près de L33-34 ; `<Route>` près de L111-112)
- Modify: `frontend/src/components/layout/Sidebar.tsx` (import icône bloc L5-32 ; `NavItem` après L193)

**Interfaces:**
- Consumes: `default export AdminRetentionDispositionPage` (Task 2/3) ; clé `retentionDisposition.navTitle` (Task 1).
- Produces: route `/admin/retention-disposition` accessible + lien sidebar ADMIN.

- [ ] **Step 1 : Déclarer le composant lazy**

Dans `frontend/src/AppRoutes.tsx`, sous la ligne `const AdminArchiveCompliancePage = lazy(...)` (L34) ajouter :
```tsx
const AdminRetentionDispositionPage = lazy(() => import('@/pages/admin/AdminRetentionDispositionPage'))
```

- [ ] **Step 2 : Ajouter la route**

Sous la ligne `<Route path="/admin/archive-compliance" element={<AdminArchiveCompliancePage />} />` (L112) ajouter :
```tsx
            <Route path="/admin/retention-disposition" element={<AdminRetentionDispositionPage />} />
```

- [ ] **Step 3 : Ajouter l'import d'icône `Trash2` dans Sidebar**

Dans `frontend/src/components/layout/Sidebar.tsx`, dans le bloc d'import `lucide-react` (L5-32), ajouter `Trash2,` (ex. après `Clock,`). `ShieldCheck` est déjà importé.

- [ ] **Step 4 : Ajouter le NavItem**

Sous la ligne `<NavItem to="/admin/archive-compliance" .../>` (L193) ajouter :
```tsx
          <NavItem to="/admin/retention-disposition" icon={Trash2} label={t('retentionDisposition.navTitle', 'Purge')} />
```

- [ ] **Step 5 : Vérifier la compilation TypeScript**

Run (depuis `frontend/`) :
```bash
npx tsc --noEmit
```
Expected : aucune sortie (0 erreur). En particulier, pas de « 'Trash2' is declared but never used » ni de référence cassée.

- [ ] **Step 6 : Commit**

```bash
git add frontend/src/AppRoutes.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(m9-8): wire purge controls route + admin sidebar entry"
```

---

### Task 5 : Vérification finale + mise à jour COMPLIANCE_MATRIX

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (L298 ; ligne « Gaps » M9 ; synthèse M9 ligne ~508-531 si un compteur M9 ✅/🟠 existe)

**Interfaces:**
- Consumes: feature complète des tâches 1-4.
- Produces: matrice à jour (preuve de complétion).

- [ ] **Step 1 : Gate complet frontend**

Run (depuis `frontend/`) :
```bash
npx tsc --noEmit
npx vitest run
```
Expected : `tsc` 0 erreur ; `vitest` tout vert (54 existants + 5 nouveaux = 59, ou le total courant + 5). Si un test échoue, corriger AVANT de continuer (règle « no failures on task completion »).

- [ ] **Step 2 : Passer L298 de 🟠 à ✅**

Dans `docs/COMPLIANCE_MATRIX.md`, remplacer la ligne 298 :

```markdown
| 8 | Archive and purge controls | ✅ | Page ADMIN `/admin/retention-disposition` : liste les documents de facture périmés en disposition PENDING (`GET /retention/pending-documents`) et permet « Conserver » (RETAINED) ou « Purger » (PURGED, modale de confirmation) via `PUT /retention/documents/{id}/disposition`. Purge = marquage de conformité non destructif (pas de suppression MinIO), tracée à l'audit. ADMIN only (PROB-065). |
```

- [ ] **Step 3 : Mettre à jour la ligne « Gaps » M9 et la synthèse**

Repérer la ligne récapitulant les gaps M9 (autour de L508-531, section synthèse) ; retirer la mention de M9 UI #8 des partiels et incrémenter le compteur ✅ / décrémenter 🟠 de la ligne du module Archives/M9 si une telle ligne chiffrée existe. Adapter au format exact présent (ne pas inventer de colonne).

- [ ] **Step 4 : Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(m9-8): mark M9 UI #8 (purge controls) compliant"
```

---

## Notes de revue finale (après Task 5)

- Lancer la revue finale whole-branch (opus) puis `superpowers:finishing-a-development-branch`.
- Si un bug réel est rencontré pendant l'implémentation → le logger dans `docs/KNOWN_ISSUES_REGISTRY.md` avec un nouveau PROB-NNN avant de committer le fix (règle Living Documentation §12).
- Push : géré hors plan (seuil 10 commits / fin de lot).
