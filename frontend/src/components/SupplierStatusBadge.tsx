import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'
import { useTranslation } from 'react-i18next'
import type { Supplier } from '@/api/suppliers'

const supplierStatusStyles: Record<Supplier['status'], string> = {
  PENDING_VERIFICATION: 'bg-yellow-100 text-yellow-800',
  ACTIVE: 'bg-green-100 text-green-800',
  SUSPENDED: 'bg-red-100 text-red-800',
}

interface SupplierStatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  status: Supplier['status']
  className?: string
}

export function SupplierStatusBadge({ status, className, ...props }: SupplierStatusBadgeProps) {
  const { t } = useTranslation()
  return (
    <span
      data-status={status}
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        supplierStatusStyles[status] || 'bg-gray-100 text-gray-800',
        className
      )}
      {...props}
    >
      {t(`supplier.status.${status}`)}
    </span>
  )
}
