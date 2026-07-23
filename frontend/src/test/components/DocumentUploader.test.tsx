import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { DocumentUploader } from '@/components/invoice/DocumentUploader'

function renderUploader() {
  return render(
    <I18nextProvider i18n={i18n}>
      <DocumentUploader onFilesChange={vi.fn()} />
    </I18nextProvider>
  )
}

describe('DocumentUploader', () => {
  // changeLanguage is async and i18n is shared across test files: without awaiting, the reset can
  // land after the next test has rendered (intermittent failures).
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('renders French drop-zone hints by default', () => {
    renderUploader()
    expect(screen.getByText('Glissez-déposez ou cliquez pour ajouter')).toBeInTheDocument()
  })

  it('renders English drop-zone hints when i18n.language is en (not the frozen FR text)', async () => {
    await i18n.changeLanguage('en')
    renderUploader()
    expect(screen.getByText('Drag and drop or click to add files')).toBeInTheDocument()
    expect(screen.queryByText('Glissez-déposez ou cliquez pour ajouter')).toBeNull()
  })
})
