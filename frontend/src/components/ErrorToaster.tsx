import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertCircle, X } from 'lucide-react'
import { getApiErrorKey, getApiErrorMessage } from '@/lib/apiError'

/**
 * Minimal error notification centre.
 *
 * AUDIT-014: 83 of the 95 mutations had no `onError`, so a failed action was
 * indistinguishable from a successful one. Rather than wiring a bespoke banner into
 * every page, mutations call {@link notifyApiError} and this single mounted
 * component renders the message. No new dependency.
 */
type Notice = { id: number; message: string | null; key: string }

let nextId = 1
const listeners = new Set<(n: Notice) => void>()

/** Publishes an API failure to the toaster. Safe to call before it mounts. */
export function notifyApiError(error: unknown, fallbackKey?: string) {
  const notice: Notice = {
    id: nextId++,
    // Backend messages are already localised; otherwise the key is resolved at
    // render time, where the toaster owns the `t` instance.
    message: getApiErrorMessage(error),
    key: fallbackKey ?? getApiErrorKey(error),
  }
  listeners.forEach((l) => l(notice))
}

export function ErrorToaster() {
  const { t } = useTranslation()
  const [notices, setNotices] = useState<Notice[]>([])

  useEffect(() => {
    const onNotice = (n: Notice) => {
      setNotices((prev) => [...prev.slice(-2), n])
      // Auto-dismiss; the user can also close it manually.
      window.setTimeout(() => {
        setNotices((prev) => prev.filter((x) => x.id !== n.id))
      }, 8000)
    }
    listeners.add(onNotice)
    return () => {
      listeners.delete(onNotice)
    }
  }, [])

  if (notices.length === 0) return null

  return (
    <div
      data-testid="error-toaster"
      className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-[min(24rem,calc(100vw-2rem))]"
    >
      {notices.map((n) => (
        <div
          key={n.id}
          role="alert"
          className="flex items-start gap-2 rounded-[4px] border border-crit/30 bg-crit-bg px-4 py-3 text-sm text-crit shadow-lg"
        >
          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <span className="flex-1">{n.message ?? t(n.key)}</span>
          <button
            type="button"
            onClick={() => setNotices((prev) => prev.filter((x) => x.id !== n.id))}
            aria-label={t('app.close', 'Fermer')}
            className="shrink-0 hover:opacity-70 transition-opacity"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ))}
    </div>
  )
}
