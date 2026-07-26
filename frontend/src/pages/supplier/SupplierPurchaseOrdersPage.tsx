import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import {
  Loader2, ArrowLeft, Package, ChevronRight, ArrowUpDown, Filter,
} from 'lucide-react'
import { formatAmount, formatDate } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'

interface PoItem {
  id: string
  itemDescription: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

interface SupplierPurchaseOrder {
  id: string
  poNumber: string
  totalAmount: number
  status: 'OPEN' | 'CLOSED' | 'CANCELLED'
  createdAt: string
  items: PoItem[]
}

const STATUS_CONFIG: Record<string, { bg: string; text: string; border: string }> = {
  OPEN:      { bg: 'bg-pos-bg',  text: 'text-pos',      border: 'border-l-pos' },
  CLOSED:    { bg: 'bg-ground',  text: 'text-ink-soft', border: 'border-l-hairline-strong' },
  CANCELLED: { bg: 'bg-crit-bg', text: 'text-crit',     border: 'border-l-crit' },
}

function StatusPill({ status }: { status: string }) {
  const { t } = useTranslation()
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.CLOSED
  return (
    <span className={`inline-flex items-center gap-1.5 pl-2 pr-3 py-1 text-xs font-semibold border-l-4 ${cfg.bg} ${cfg.text} ${cfg.border}`}>
      {t(`po.statusValue.${status}`, status)}
    </span>
  )
}

function PoDetailView({ po, onBack }: { po: SupplierPurchaseOrder; onBack: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          aria-label={t('app.back', 'Retour')}
          className="flex items-center justify-center w-9 h-9 border border-hairline rounded-[4px] hover:bg-ground text-ink-soft transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <p className="text-xs font-semibold text-primary uppercase tracking-wider">{t('po.detail.title', 'Détail du bon de commande')}</p>
          <h1 className="text-xl font-bold text-ink num">{po.poNumber}</h1>
        </div>
        <div className="ml-auto"><StatusPill status={po.status} /></div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Panel className="p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.total', 'Montant total')}</p>
          <p className="text-sm font-bold text-ink num">{formatAmount(po.totalAmount)} XAF</p>
        </Panel>
        <Panel className="p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.statusLabel', 'Statut')}</p>
          <p className="text-sm font-bold text-ink">{t(`po.statusValue.${po.status}`, po.status)}</p>
        </Panel>
        <Panel className="p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-ink-faint mb-1">{t('po.createdAt', 'Date de création')}</p>
          <p className="text-sm font-bold text-ink num">{formatDate(po.createdAt)}</p>
        </Panel>
      </div>

      <Panel className="overflow-x-auto">
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
      </Panel>
    </div>
  )
}

type SortKey = 'poNumber' | 'totalAmount' | 'createdAt' | 'status'

export default function SupplierPurchaseOrdersPage() {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<SupplierPurchaseOrder | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [sortKey, setSortKey] = useState<SortKey>('createdAt')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc')

  const { data, isLoading } = useQuery({
    queryKey: ['supplier-purchase-orders'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierPurchaseOrder[] }>('/supplier/purchase-orders/all')
      return data.data ?? []
    },
  })

  const rows = useMemo(() => {
    const list = (data ?? []).filter(po => !statusFilter || po.status === statusFilter)
    const dir = sortDir === 'asc' ? 1 : -1
    return [...list].sort((a, b) => {
      switch (sortKey) {
        case 'totalAmount': return (a.totalAmount - b.totalAmount) * dir
        case 'createdAt':   return (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()) * dir
        default:            return a[sortKey].localeCompare(b[sortKey]) * dir
      }
    })
  }, [data, statusFilter, sortKey, sortDir])

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortDir(d => (d === 'asc' ? 'desc' : 'asc'))
    else { setSortKey(key); setSortDir('asc') }
  }

  if (selected) {
    return <PoDetailView po={selected} onBack={() => setSelected(null)} />
  }

  const SortHeader = ({ label, k, className = '' }: { label: string; k: SortKey; className?: string }) => (
    <th className={`px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide ${className}`}>
      <button onClick={() => toggleSort(k)} className="inline-flex items-center gap-1 hover:text-ink-soft">
        {label} <ArrowUpDown className={`w-3 h-3 ${sortKey === k ? 'text-primary' : 'text-ink-faint'}`} />
      </button>
    </th>
  )

  return (
    <div className="space-y-5">
      <PageHeader
        title={t('supplier.portal.purchaseOrders', 'Mes commandes')}
        subtitle={t('po.portalSubtitle', 'Les bons de commande qui vous ont été adressés par OCT')}
      />

      <Panel className="p-4 flex items-center gap-3">
        <Filter className="w-4 h-4 text-ink-faint shrink-0" />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/20 bg-surface text-ink"
        >
          <option value="">{t('app.allStatus', 'Tous les statuts')}</option>
          {(['OPEN', 'CLOSED', 'CANCELLED']).map(s => (
            <option key={s} value={s}>{t(`po.statusValue.${s}`, s)}</option>
          ))}
        </select>
      </Panel>

      <Panel className="overflow-x-auto">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
            <Package className="w-10 h-10" />
            <p className="text-sm font-medium">{t('po.empty', 'Aucun bon de commande')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-ground">
                <tr className="text-left">
                  <SortHeader label={t('po.number', 'N° commande')} k="poNumber" />
                  <SortHeader label={t('po.total', 'Montant')} k="totalAmount" className="text-right" />
                  <SortHeader label={t('po.statusLabel', 'Statut')} k="status" />
                  <SortHeader label={t('po.createdAt', 'Date')} k="createdAt" className="hidden md:table-cell" />
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {rows.map((po) => (
                  <tr
                    key={po.id}
                    className="cursor-pointer transition-colors hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]"
                    onClick={() => setSelected(po)}
                  >
                    <td className="px-4 py-3 num text-xs font-semibold text-ink">{po.poNumber}</td>
                    <td className="px-4 py-3 num font-medium text-ink-soft text-right">{formatAmount(po.totalAmount)} <span className="text-ink-faint">XAF</span></td>
                    <td className="px-4 py-3"><StatusPill status={po.status} /></td>
                    <td className="px-4 py-3 text-ink-soft text-xs hidden md:table-cell num">{formatDate(po.createdAt)}</td>
                    <td className="px-4 py-3 text-right"><ChevronRight className="w-4 h-4 text-ink-faint ml-auto" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}
