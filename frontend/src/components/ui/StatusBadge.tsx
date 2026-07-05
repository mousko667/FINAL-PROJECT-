import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'
import type { InvoiceStatus } from '@/types/invoice'
import { useTranslation } from 'react-i18next'

interface StatusConfig {
  dot: string
  bg: string
  text: string
  border: string
}

/**
 * Registre semantic mapping (Track B / Lot B2) — 5 semantics + 2 neutrals.
 * N1 (warn) vs N2 (hot) are intentionally distinct hues (escalation reads at
 * a glance). VALIDE / BON_A_PAYER / PAYE are all "pos" but graduated in
 * strength: VALIDE and BON_A_PAYER share the light bg-pos-bg/text-pos tint
 * (still-in-flight positive states), while PAYE — the terminal, fully
 * settled state — gets the saturated solid `bg-pos` fill with light text,
 * so the eye can tell "paid" apart from "on the way to being paid".
 */
const statusConfig: Record<InvoiceStatus, StatusConfig> = {
  BROUILLON:        { dot: 'bg-ink-faint',  bg: 'bg-ground',   text: 'text-ink-soft',    border: 'border-hairline' },
  SOUMIS:           { dot: 'bg-info',       bg: 'bg-info-bg',  text: 'text-info',        border: 'border-info/30' },
  EN_VALIDATION_N1: { dot: 'bg-warn',       bg: 'bg-warn-bg',  text: 'text-warn',        border: 'border-warn/30' },
  EN_VALIDATION_N2: { dot: 'bg-hot',        bg: 'bg-hot-bg',   text: 'text-hot',         border: 'border-hot/30' },
  VALIDE:           { dot: 'bg-pos',        bg: 'bg-pos-bg',   text: 'text-pos',         border: 'border-pos/30' },
  BON_A_PAYER:      { dot: 'bg-pos',        bg: 'bg-pos-bg',   text: 'text-pos',         border: 'border-pos/40' },
  PAYE:             { dot: 'bg-pos',        bg: 'bg-pos',      text: 'text-pos-bg',      border: 'border-pos' },
  ARCHIVE:          { dot: 'bg-ink-faint',  bg: 'bg-ground',   text: 'text-ink-faint',   border: 'border-hairline' },
  REJETE:           { dot: 'bg-crit',       bg: 'bg-crit-bg',  text: 'text-crit',        border: 'border-crit/30' },
}

interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  status: InvoiceStatus
  className?: string
  variant?: 'pill' | 'dot-only' | 'inline'
}

export function StatusBadge({ status, className, variant = 'pill', ...props }: StatusBadgeProps) {
  const { t } = useTranslation()
  const cfg = statusConfig[status] ?? { dot: 'bg-ink-faint', bg: 'bg-ground', text: 'text-ink-faint', border: 'border-hairline' }

  if (variant === 'dot-only') {
    return (
      <span
        data-status={status}
        className={cn('inline-block w-2.5 h-2.5 rounded-full', cfg.dot, className)}
        title={t(`status.${status}`)}
        {...props}
      />
    )
  }

  if (variant === 'inline') {
    return (
      <span
        data-status={status}
        className={cn('inline-flex items-center gap-1.5 text-xs font-medium', cfg.text, className)}
        {...props}
      >
        <span className={cn('w-2 h-2 rounded-full shrink-0', cfg.dot)} />
        {t(`status.${status}`)}
      </span>
    )
  }

  return (
    <span
      data-status={status}
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border',
        cfg.bg, cfg.text, cfg.border,
        className
      )}
      {...props}
    >
      <span className={cn('w-1.5 h-1.5 rounded-full shrink-0', cfg.dot)} />
      {t(`status.${status}`)}
    </span>
  )
}
