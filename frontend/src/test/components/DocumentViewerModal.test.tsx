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

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { ViewerToolbar } from '@/components/invoice/ViewerToolbar'
import { DocumentViewerModal } from '@/components/invoice/DocumentViewerModal'

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

  it('déclenche les callbacks de navigation de page', async () => {
    const u = userEvent.setup()
    const onPrevPage = vi.fn()
    const onNextPage = vi.fn()
    renderToolbar({ pageNumber: 2, numPages: 5, onPrevPage, onNextPage })
    await u.click(screen.getByLabelText(/page précédente/i)); expect(onPrevPage).toHaveBeenCalled()
    await u.click(screen.getByLabelText(/page suivante/i)); expect(onNextPage).toHaveBeenCalled()
  })

  it('désactive les boutons de navigation aux bornes', () => {
    const { unmount } = render(
      <I18nextProvider i18n={i18n}>
        <ViewerToolbar zoom={1} rotation={0} canZoomIn={true} canZoomOut={true}
          onZoomIn={vi.fn()} onZoomOut={vi.fn()} onRotate={vi.fn()} onReset={vi.fn()}
          pageNumber={1} numPages={5} onPrevPage={vi.fn()} onNextPage={vi.fn()} />
      </I18nextProvider>
    )
    expect(screen.getByLabelText(/page précédente/i)).toHaveProperty('disabled', true)
    unmount()
    render(
      <I18nextProvider i18n={i18n}>
        <ViewerToolbar zoom={1} rotation={0} canZoomIn={true} canZoomOut={true}
          onZoomIn={vi.fn()} onZoomOut={vi.fn()} onRotate={vi.fn()} onReset={vi.fn()}
          pageNumber={5} numPages={5} onPrevPage={vi.fn()} onNextPage={vi.fn()} />
      </I18nextProvider>
    )
    expect(screen.getByLabelText(/page suivante/i)).toHaveProperty('disabled', true)
  })
})

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

  it('zoom+ est bloqué à ZOOM_MAX=3 après de nombreux clics', async () => {
    const u = userEvent.setup()
    renderModal()
    // 12 clics de zoom+ — ZOOM_MAX=3, ZOOM_MIN=0.5, ZOOM_STEP=0.25 → plafond atteint avant 12 clics
    for (let i = 0; i < 12; i++) {
      await u.click(screen.getByLabelText(/zoom avant/i))
    }
    const scale = Number(screen.getByTestId('pdf-page').getAttribute('data-scale'))
    expect(scale).toBeLessThanOrEqual(3)
    expect(scale).toBe(3)
    expect(screen.getByLabelText(/zoom avant/i)).toHaveProperty('disabled', true)
  })

  it('une image en erreur affiche le fallback loadError + lien download', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <DocumentViewerModal url="http://x/broken.png" filename="broken.png" fileType="image/png" onClose={vi.fn()} />
      </I18nextProvider>
    )
    // Avant l'erreur, l'image est présente
    const img = screen.getByRole('img')
    // Déclenche l'erreur de chargement
    fireEvent.error(img)
    // L'image disparaît, le fallback s'affiche
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.getByText(/impossible de charger/i)).toBeDefined()
    // Lien de téléchargement présent dans le fallback
    const links = screen.getAllByRole('link')
    expect(links.some(l => l.getAttribute('download') !== null)).toBe(true)
  })
})
