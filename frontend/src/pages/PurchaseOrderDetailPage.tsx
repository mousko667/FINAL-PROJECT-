import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, ArrowLeft, Package } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatAmount, formatDate } from '@/lib/format'

interface PoItem {
  id: string
  itemDescription: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

interface PurchaseOrderDetail {
  id: string
  poNumber: string
  supplierName?: string
  totalAmount: number
  currency?: string
  status: string
  createdAt: string
  items?: PoItem[]
}

const STATUS_CLASS: Record<string, string> = {
  OPEN: 'bg-pos-bg text-pos',
  CLOSED: 'bg-ground text-ink-soft',
  CANCELLED: 'bg-crit-bg text-crit',
}

export default function PurchaseOrderDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()

  const goBack = () => {
    if (location.key !== 'default') navigate(-1)
    else navigate('/purchase-orders')
  }

  const { data: po, isLoading, isError } = useQuery({
    queryKey: ['purchase-order', id],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PurchaseOrderDetail }>(`/purchase-orders/${id}`)
      return data.data
    },
    enabled: !!id,
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <PageHeader
          title={
            <span className="flex items-center gap-3">
              <button onClick={goBack} aria-label={t('app.back', 'Retour')}
                className="p-2 -ml-2 rounded-[4px] hover:bg-white/10 text-white/80 shrink-0">
                <ArrowLeft className="w-5 h-5" />
              </button>
              <span className="num">{po?.poNumber ?? t('po.detail.title', 'Bon de commande')}</span>
            </span>
          }
          subtitle={po?.supplierName}
        />

        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
        ) : isError || !po ? (
          <div className="text-center py-20">
            <p className="text-crit text-sm mb-4">{t('app.error')}</p>
            <button onClick={goBack} className="text-gold-deep underline text-sm">{t('app.back')}</button>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <div className="bg-surface rounded-[4px] border border-hairline p-4">
                <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.total', 'Montant total')}</p>
                <p className="text-sm font-bold text-ink num">{formatAmount(po.totalAmount)} {po.currency ?? 'XAF'}</p>
              </div>
              <div className="bg-surface rounded-[4px] border border-hairline p-4">
                <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.statusLabel', 'Statut')}</p>
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_CLASS[po.status] ?? 'bg-ground text-ink-soft'}`}>
                  {t(`po.statusValue.${po.status}`, po.status)}
                </span>
              </div>
              <div className="bg-surface rounded-[4px] border border-hairline p-4">
                <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('invoice.supplier', 'Fournisseur')}</p>
                <p className="text-sm font-bold text-ink">{po.supplierName ?? '—'}</p>
              </div>
              <div className="bg-surface rounded-[4px] border border-hairline p-4">
                <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.createdAt', 'Date de création')}</p>
                <p className="text-sm font-bold text-ink num">{formatDate(po.createdAt)}</p>
              </div>
            </div>

            <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
              {!po.items?.length ? (
                <div className="flex flex-col items-center justify-center py-12 gap-3 text-ink-faint">
                  <Package className="w-8 h-8" />
                  <p className="text-sm">{t('po.noItems', 'Aucune ligne')}</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-ground">
                    <tr>
                      <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('po.item.description', 'Désignation')}</th>
                      <th className="text-right px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('po.item.quantity', 'Qté')}</th>
                      <th className="text-right px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('po.item.unitPrice', 'PU')}</th>
                      <th className="text-right px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('po.item.lineTotal', 'Total')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-hairline">
                    {po.items.map((it) => (
                      <tr key={it.id}>
                        <td className="px-4 py-3 text-ink">{it.itemDescription}</td>
                        <td className="px-4 py-3 text-right num text-ink-soft">{it.quantity}</td>
                        <td className="px-4 py-3 text-right num text-ink-soft">{formatAmount(it.unitPrice)}</td>
                        <td className="px-4 py-3 text-right num font-medium text-ink">{formatAmount(it.lineTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </>
        )}
      </div>
    </PageRoleGuard>
  )
}
