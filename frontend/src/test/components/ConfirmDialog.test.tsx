import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'

function renderDialog(props: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const onConfirm = props.onConfirm ?? vi.fn()
  const onCancel = props.onCancel ?? vi.fn()
  const utils = render(
    <I18nextProvider i18n={i18n}>
      <ConfirmDialog
        open={true}
        title="Delete this item?"
        message="This action cannot be undone."
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...props}
      />
    </I18nextProvider>
  )
  return { ...utils, onConfirm, onCancel }
}

describe('ConfirmDialog', () => {
  afterEach(() => {
    cleanup()
    i18n.changeLanguage('fr')
  })

  it('does not render anything when open=false', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <ConfirmDialog open={false} title="t" message="m" onConfirm={vi.fn()} onCancel={vi.fn()} />
      </I18nextProvider>
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('renders title and message with role="dialog" when open', () => {
    renderDialog()
    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()
    expect(screen.getByText('Delete this item?')).toBeInTheDocument()
    expect(screen.getByText('This action cannot be undone.')).toBeInTheDocument()
  })

  it('calls onConfirm when the confirm button is clicked', () => {
    const { onConfirm } = renderDialog()
    fireEvent.click(screen.getByText(i18n.t('app.confirm')))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the cancel button is clicked', () => {
    const { onCancel } = renderDialog()
    fireEvent.click(screen.getByText(i18n.t('app.cancel')))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when Escape is pressed', () => {
    const { onCancel } = renderDialog()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('supports custom confirm/cancel labels', () => {
    renderDialog({ confirmLabel: 'Yes, revoke', cancelLabel: 'No, keep it' })
    expect(screen.getByText('Yes, revoke')).toBeInTheDocument()
    expect(screen.getByText('No, keep it')).toBeInTheDocument()
  })

  it('applies danger styling when variant="danger"', () => {
    renderDialog({ variant: 'danger' })
    const confirmBtn = screen.getByText(i18n.t('app.confirm'))
    expect(confirmBtn.className).toMatch(/red/)
  })
})
