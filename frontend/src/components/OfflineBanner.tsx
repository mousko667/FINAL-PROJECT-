import { useTranslation } from 'react-i18next'
import { WifiOff, RefreshCw } from 'lucide-react'

/**
 * Full-screen "service unavailable" state.
 *
 * AUDIT-014: replaces the silent logout that a backend outage used to trigger.
 * The session is deliberately NOT cleared — the user stays signed in and simply
 * retries once the backend answers again.
 */
export function OfflineBanner({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()

  return (
    <div
      role="alert"
      data-testid="offline-banner"
      className="flex flex-col items-center justify-center h-screen gap-4 bg-ground px-6 text-center"
    >
      <WifiOff className="w-10 h-10 text-crit" />
      <p className="text-base font-semibold text-ink">
        {t('error.network')}
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-[4px] bg-oct-navy text-white text-sm font-medium hover:opacity-90 transition-opacity"
      >
        <RefreshCw className="w-4 h-4" />
        {t('app.retry', 'Réessayer')}
      </button>
    </div>
  )
}
