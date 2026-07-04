import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
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
  verdict === 'MISMATCH' || verdict === 'MISSING_IN_PO' ? 'bg-red-50' : ''

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
      <div className="max-w-5xl mx-auto space-y-6">
        <Link to="/matching" className="text-sm text-blue-600">
          {t('matching.back')}
        </Link>

        {isLoading ? (
          <div className="flex justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : isError || !data ? (
          <p className="text-sm text-red-500">{t('matching.error')}</p>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <h1 className="text-2xl font-bold text-gray-900">
                {data.summary.invoiceNumber} · {data.summary.supplierName}
              </h1>
              <div className="flex gap-2">
                {(['csv', 'excel', 'pdf'] as const).map((f) => (
                  <button
                    key={f}
                    onClick={() => exportReport(f)}
                    className="text-sm border rounded-lg px-3 py-1.5"
                  >
                    {t('matching.export')} {f.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>

            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2">{t('matching.description')}</th>
                  <th>{t('matching.poQty')}</th>
                  <th>{t('matching.poPrice')}</th>
                  <th>{t('matching.received')}</th>
                  <th>{t('matching.invQty')}</th>
                  <th>{t('matching.invPrice')}</th>
                  <th>{t('matching.qtyVariance')}</th>
                  <th>{t('matching.priceVariance')}</th>
                  <th>{t('matching.verdict')}</th>
                  <th>{t('app.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {data.lines.map((l: LineComparison, i: number) => (
                  <tr key={i} className={`border-b ${rowClass(l.verdict)}`}>
                    <td className="py-2 font-medium">{l.description}</td>
                    <td>{num(l.poQuantity)}</td>
                    <td>{num(l.poUnitPrice)}</td>
                    <td>{num(l.receivedQuantity)}</td>
                    <td>{num(l.invoiceQuantity)}</td>
                    <td>{num(l.invoiceUnitPrice)}</td>
                    <td>{pct(l.qtyVariancePct)}</td>
                    <td>{pct(l.priceVariancePct)}</td>
                    <td>
                      {l.resolutionStatus === 'RESOLVED' ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">
                          {t('matching.resolved')}
                        </span>
                      ) : t(`matching.verdicts.${l.verdict}`)}
                    </td>
                    <td className="py-2">
                      {l.verdict === 'MISMATCH' && l.resolutionStatus !== 'RESOLVED' && l.poLineId && (
                        <button
                          onClick={() => setResolvingLine({ poLineId: l.poLineId!, description: l.description })}
                          className="text-xs bg-blue-50 text-blue-600 hover:bg-blue-100 px-2 py-1 rounded"
                        >
                          {t('matching.resolveBtn')}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

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
