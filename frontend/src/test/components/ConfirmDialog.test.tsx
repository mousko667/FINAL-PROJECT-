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
  // changeLanguage is async and i18n is shared across test files: without awaiting, the reset can
  // land after the next test has rendered (intermittent failures).
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
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
    expect(confirmBtn.className).toMatch(/crit/)
  })

  it('backdrop click cancels without bubbling to an outer handler', () => {
    // Guards against the nested-modal double-close: when a ConfirmDialog is
    // rendered inside another backdrop-closing modal, clicking its own backdrop
    // must not also trigger the parent's onClick.
    const onCancel = vi.fn()
    const outerClick = vi.fn()
    render(
      <I18nextProvider i18n={i18n}>
        <div onClick={outerClick}>
          <ConfirmDialog open={true} title="t" message="m" onConfirm={vi.fn()} onCancel={onCancel} />
        </div>
      </I18nextProvider>
    )
    // The backdrop is the dialog's outermost element (parent of role="dialog").
    const backdrop = screen.getByRole('dialog').parentElement as HTMLElement
    fireEvent.click(backdrop)
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(outerClick).not.toHaveBeenCalled()
  })
})
