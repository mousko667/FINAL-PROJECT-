import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Panel } from '@/components/ui/Panel'
import apiClient from '@/services/apiClient'
import { getMatchingLines, resolveMatchingLine, type LineComparison } from '@/services/matchingService'
import MatchingLineResolveModal from '@/components/matching/MatchingLineResolveModal'

// Real role names — mirrors MatchingListPage. Excludes ROLE_ADMIN (SoD: no financial access).
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

const pct = (v: number | null) => (v == null ? '—' : `${v}%`)
const num = (v: number | null) => (v == null ? '—' : String(v))

const rowClass = (verdict: string) =>
  verdict === 'MISMATCH' || verdict === 'MISSING_IN_PO' ? 'bg-crit-bg' : ''

export default function MatchingDetailPage() {
  const { t } = useTranslation()
  const { invoiceId } = useParams<{ invoiceId: string }>()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['matching-lines', invoiceId],
    queryFn: () => getMatchingLines(invoiceId!),
    enabled: !!invoiceId,
  })

  const [resolvingLine, setResolvingLine] = useState<{ poLineId: string; description: string } | null>(null)

  const handleResolve = async (reason: string) => {
    if (!resolvingLine || !invoiceId) return
    await resolveMatchingLine(invoiceId, resolvingLine.poLineId, reason)
    refetch()
  }

  const exportReport = async (format: string) => {
    const res = await apiClient.get(`/invoices/${invoiceId}/matching/export`, {
      params: { format },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `matching_${invoiceId}.${format === 'excel' ? 'xlsx' : format}`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <PageRoleGuard allowedRoles={STAFF_ROLES}>
      <div className="max-w-5xl mx-auto space-y-6 page-enter">
        <Link to="/matching" className="text-sm text-gold-deep hover:underline">
          {t('matching.back')}
        </Link>

        {isLoading ? (
          <div className="flex justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        ) : isError || !data ? (
          <p className="text-sm text-crit">{t('matching.error')}</p>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <h1 className="text-2xl font-bold text-ink">
                <span className="num">{data.summary.invoiceNumber}</span> · {data.summary.supplierName}
              </h1>
              <div className="flex gap-2">
                {(['csv', 'excel', 'pdf'] as const).map((f) => (
                  <button
                    key={f}
                    onClick={() => exportReport(f)}
                    className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 text-ink-soft hover:bg-ground"
                  >
                    {t('matching.export')} {f.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>

            <Panel className="overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-ground">
                  <tr>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.description')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.poQty')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.poPrice')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.received')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.invQty')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.invPrice')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.qtyVariance')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.priceVariance')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.verdict')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('app.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {data.lines.map((l: LineComparison, i: number) => (
                    <tr key={i} className={rowClass(l.verdict)}>
                      <td className="px-4 py-3 font-medium text-ink">{l.description}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{num(l.poQuantity)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{num(l.poUnitPrice)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{num(l.receivedQuantity)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{num(l.invoiceQuantity)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{num(l.invoiceUnitPrice)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{pct(l.qtyVariancePct)}</td>
                      <td className="num px-4 py-3 text-right text-ink-soft">{pct(l.priceVariancePct)}</td>
                      <td className="px-4 py-3 text-ink-soft">
                        {l.resolutionStatus === 'RESOLVED' ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-pos-bg text-pos">
                            {t('matching.resolved')}
                          </span>
                        ) : t(`matching.verdicts.${l.verdict}`)}
                      </td>
                      <td className="px-4 py-3">
                        {l.verdict === 'MISMATCH' && l.resolutionStatus !== 'RESOLVED' && l.poLineId && (
                          <button
                            onClick={() => setResolvingLine({ poLineId: l.poLineId!, description: l.description })}
                            className="text-xs bg-info-bg text-info hover:opacity-80 px-2 py-1 rounded"
                          >
                            {t('matching.resolveBtn')}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>

            <MatchingLineResolveModal
              isOpen={!!resolvingLine}
              onClose={() => setResolvingLine(null)}
              onResolve={handleResolve}
              poLineId={resolvingLine?.poLineId || ''}
              description={resolvingLine?.description || ''}
            />
          </>
        )}
      </div>
    </PageRoleGuard>
  )
}
