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
