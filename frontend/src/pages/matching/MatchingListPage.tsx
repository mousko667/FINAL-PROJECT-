import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Panel } from '@/components/ui/Panel'
import { listMatching, type MatchingSummary } from '@/services/matchingService'

const rowHoverTint = 'hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] transition-colors'

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
      <div className="max-w-5xl mx-auto space-y-6 page-enter">
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('matching.pageTitle')}</h1>
          <p className="text-sm text-ink-soft mt-0.5">{t('matching.pageSubtitle')}</p>
        </div>

        <div className="flex gap-3">
          <input
            className="border border-hairline rounded-[4px] px-3 py-1.5 text-sm flex-1 bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30"
            placeholder={t('matching.search')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            className="border border-hairline rounded-[4px] px-3 py-1.5 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30"
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
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        ) : isError ? (
          <p className="text-sm text-crit">{t('matching.error')}</p>
        ) : !data || data.content.length === 0 ? (
          <p className="text-sm text-ink-faint">{t('matching.empty')}</p>
        ) : (
          <Panel className="overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-ground">
                <tr>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.invoiceNumber')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.supplier')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.poNumber')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.status')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {data.content.map((m: MatchingSummary) => (
                  <tr
                    key={m.invoiceId}
                    className={`cursor-pointer ${rowHoverTint}`}
                    onClick={() => navigate(`/matching/${m.invoiceId}`)}
                  >
                    <td className="num px-4 py-3 font-medium text-ink">{m.invoiceNumber}</td>
                    <td className="px-4 py-3 text-ink-soft">{m.supplierName}</td>
                    <td className="num px-4 py-3 text-ink-soft">{m.purchaseOrderNumber ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-soft">{m.status ? t(`matching.statuses.${m.status}`) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}
      </div>
    </PageRoleGuard>
  )
}
