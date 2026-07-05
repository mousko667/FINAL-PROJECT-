import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ShieldCheck } from 'lucide-react'
import apiClient from '@/services/apiClient'
import type { ApiResponse } from '@/types/invoice'
import { formatDateTime } from '@/lib/format'

type RetentionStatus = 'CONFORME' | 'ATTENTION' | 'NON_CONFORME'

interface RetentionCompliance {
  status: RetentionStatus
  retentionYears: number
  active: boolean
  lastSweepAt?: string | null
  lastFlaggedCount?: number | null
  sweepOverdue: boolean
  updatedAt?: string | null
}

const STATUS_STYLE: Record<RetentionStatus, { badge: string; key: string }> = {
  CONFORME: { badge: 'bg-green-50 text-green-700 border-green-200', key: 'admin.audit.retention.statusConforme' },
  ATTENTION: { badge: 'bg-amber-50 text-amber-700 border-amber-200', key: 'admin.audit.retention.statusAttention' },
  NON_CONFORME: { badge: 'bg-red-50 text-red-700 border-red-200', key: 'admin.audit.retention.statusNonConforme' },
}

/**
 * M10 #10 — retention compliance indicator on the admin audit screen. Reads the computed
 * compliance status (ADMIN-only endpoint) and renders a status badge plus policy details.
 * Hidden (null) on error / 403, like the anomaly panel.
 */
export default function RetentionComplianceCard() {
  const { t } = useTranslation()
  const { data, isError } = useQuery<RetentionCompliance>({
    queryKey: ['retention-compliance'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<RetentionCompliance>>('/retention-policy/compliance')
      return data.data
    },
    retry: false,
  })

  if (isError || !data) return null

  const style = STATUS_STYLE[data.status]
  if (!style) return null

  return (
    <div className="bg-white rounded-xl border p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-gray-800">{t('admin.audit.retention.title')}</h2>
        </div>
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full border ${style.badge}`}>
          {t(style.key)}
        </span>
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.period')}</dt>
          <dd className="text-gray-700">{t('admin.audit.retention.years', { count: data.retentionYears })}</dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.active')}</dt>
          <dd className="text-gray-700">{data.active ? t('admin.audit.retention.yes') : t('admin.audit.retention.no')}</dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.lastSweep')}</dt>
          <dd className="text-gray-700">
            {data.lastSweepAt ? formatDateTime(data.lastSweepAt) : t('admin.audit.retention.never')}
          </dd>
        </div>
        <div>
          <dt className="text-gray-400 text-xs">{t('admin.audit.retention.flagged')}</dt>
          <dd className="text-gray-700">{data.lastFlaggedCount ?? 0}</dd>
        </div>
      </dl>
      {data.sweepOverdue && (
        <p className="mt-3 text-xs text-amber-700">{t('admin.audit.retention.sweepOverdue')}</p>
      )}
    </div>
  )
}
