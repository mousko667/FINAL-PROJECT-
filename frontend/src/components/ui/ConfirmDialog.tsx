import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
  /** Visual emphasis for the confirm button — 'danger' for destructive actions. */
  variant?: 'default' | 'danger'
}

/**
 * Generic controlled confirmation dialog, used in front of destructive actions
 * (delete / revoke / disable / reset) instead of the native window.confirm().
 * Accessible: role="dialog", focuses the confirm button on open, Escape cancels.
 */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  variant = 'default',
}: ConfirmDialogProps) {
  const { t } = useTranslation()
  const confirmBtnRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    confirmBtnRef.current?.focus()
  }, [open])

  if (!open) return null

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.stopPropagation()
      onCancel()
    }
  }

  const confirmBtnClass = variant === 'danger'
    ? 'flex items-center gap-2 px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 transition-colors'
    : 'flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors'

  const dialog = (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={onCancel}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-5"
      >
        <div>
          <h2 id="confirm-dialog-title" className="text-lg font-bold text-gray-900">{title}</h2>
          <p id="confirm-dialog-message" className="text-sm text-gray-600 mt-2">{message}</p>
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onCancel} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 transition-colors">
            {cancelLabel ?? t('app.cancel')}
          </button>
          <button type="button" ref={confirmBtnRef} onClick={onConfirm} className={confirmBtnClass}>
            {confirmLabel ?? t('app.confirm')}
          </button>
        </div>
      </div>
    </div>
  )

  return createPortal(dialog, document.body)
}
