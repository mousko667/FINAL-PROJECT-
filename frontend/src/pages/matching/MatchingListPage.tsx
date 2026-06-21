import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { listMatching, type MatchingSummary } from '@/services/matchingService'

// Real role names from constants/roles.ts — excludes ROLE_ADMIN (SoD: no financial access)
const STAFF_ROLES = [
  'ROLE_DAF',
  'ROLE_ASSISTANT_COMPTABLE',
  'ROLE_VALIDATEUR_N1_DRH',
  'ROLE_VALIDATEUR_N1_DG',
  'ROLE_VALIDATEUR_N1_INFO',
  'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N1_TERM',
  'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE',
  'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N2_INFRA',
  'ROLE_VALIDATEUR_N1_TECH',
  'ROLE_VALIDATEUR_N2_TECH',
]

export default function MatchingListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['matching-list', status, search],
    queryFn: () => listMatching({ status: status || undefined, search: search || undefined }),
  })

  return (
    <PageRoleGuard allowedRoles={STAFF_ROLES}>
      <div className="max-w-5xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('matching.pageTitle')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('matching.pageSubtitle')}</p>
        </div>

        <div className="flex gap-3">
          <input
            className="border rounded-lg px-3 py-1.5 text-sm flex-1"
            placeholder={t('matching.search')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            className="border rounded-lg px-3 py-1.5 text-sm"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">{t('matching.all')}</option>
            {(['MATCHED', 'PARTIAL', 'MISMATCH', 'OVERRIDDEN'] as const).map((s) => (
              <option key={s} value={s}>
                {t(`matching.statuses.${s}`)}
              </option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : isError ? (
          <p className="text-sm text-red-500">{t('matching.error')}</p>
        ) : !data || data.content.length === 0 ? (
          <p className="text-sm text-gray-400">{t('matching.empty')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="py-2">{t('matching.invoiceNumber')}</th>
                <th>{t('matching.supplier')}</th>
                <th>{t('matching.poNumber')}</th>
                <th>{t('matching.status')}</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((m: MatchingSummary) => (
                <tr
                  key={m.invoiceId}
                  className="border-b hover:bg-gray-50 cursor-pointer"
                  onClick={() => navigate(`/matching/${m.invoiceId}`)}
                >
                  <td className="py-2 font-medium">{m.invoiceNumber}</td>
                  <td>{m.supplierName}</td>
                  <td>{m.purchaseOrderNumber ?? '—'}</td>
                  <td>{m.status ? t(`matching.statuses.${m.status}`) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </PageRoleGuard>
  )
}
