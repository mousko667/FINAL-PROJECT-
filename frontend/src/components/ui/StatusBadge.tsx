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

const statusConfig: Record<InvoiceStatus, StatusConfig> = {
  BROUILLON:        { dot: 'bg-slate-400',    bg: 'bg-slate-50',   text: 'text-slate-700',  border: 'border-slate-300' },
  SOUMIS:           { dot: 'bg-blue-500',     bg: 'bg-blue-50',    text: 'text-blue-700',   border: 'border-blue-300' },
  EN_VALIDATION_N1: { dot: 'bg-amber-500',    bg: 'bg-amber-50',   text: 'text-amber-800',  border: 'border-amber-300' },
  EN_VALIDATION_N2: { dot: 'bg-orange-500',   bg: 'bg-orange-50',  text: 'text-orange-800', border: 'border-orange-300' },
  VALIDE:           { dot: 'bg-teal-500',     bg: 'bg-teal-50',    text: 'text-teal-800',   border: 'border-teal-300' },
  BON_A_PAYER:      { dot: 'bg-green-500',    bg: 'bg-green-50',   text: 'text-green-800',  border: 'border-green-300' },
  PAYE:             { dot: 'bg-emerald-600',  bg: 'bg-emerald-50', text: 'text-emerald-800',border: 'border-emerald-300' },
  ARCHIVE:          { dot: 'bg-gray-400',     bg: 'bg-gray-50',    text: 'text-gray-600',   border: 'border-gray-300' },
  REJETE:           { dot: 'bg-red-500',      bg: 'bg-red-50',     text: 'text-red-700',    border: 'border-red-300' },
}

interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  status: InvoiceStatus
  className?: string
  variant?: 'pill' | 'dot-only' | 'inline'
}

export function StatusBadge({ status, className, variant = 'pill', ...props }: StatusBadgeProps) {
  const { t } = useTranslation()
  const cfg = statusConfig[status] ?? { dot: 'bg-gray-400', bg: 'bg-gray-50', text: 'text-gray-600', border: 'border-gray-200' }

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
