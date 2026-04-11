import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'
import type { InvoiceStatus } from '@/types/invoice'
import { useTranslation } from 'react-i18next'

const statusStyles: Record<InvoiceStatus, string> = {
  BROUILLON: 'bg-gray-100 text-gray-700',
  SOUMIS: 'bg-blue-100 text-blue-700',
  EN_VALIDATION_N1: 'bg-yellow-100 text-yellow-700',
  EN_VALIDATION_N2: 'bg-orange-100 text-orange-700',
  VALIDE: 'bg-teal-100 text-teal-700',
  BON_A_PAYER: 'bg-green-100 text-green-700',
  PAYE: 'bg-emerald-100 text-emerald-700',
  ARCHIVE: 'bg-slate-100 text-slate-600',
  REJETE: 'bg-red-100 text-red-700',
}

interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  status: InvoiceStatus
  className?: string
}

export function StatusBadge({ status, className, ...props }: StatusBadgeProps) {
  const { t } = useTranslation()
  return (
    <span
      data-status={status}
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        statusStyles[status],
        className
      )}
      {...props}
    >
      {t(`status.${status}`)}
    </span>
  )
}
