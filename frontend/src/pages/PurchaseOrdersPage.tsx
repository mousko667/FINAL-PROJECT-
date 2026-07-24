import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { useAppSelector } from '@/store/hooks'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, ChevronLeft, ChevronRight, Upload, FileSpreadsheet } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatAmount, formatDate } from '@/lib/format'

interface PurchaseOrder {
  id: string
  poNumber: string
  supplierName?: string
  totalAmount: number
  currency?: string
  status: string
  createdAt: string
  items?: Array<{ itemDescription: string; quantity: number; unitPrice: number }>
}

interface Supplier { id: string; companyName: string }

export default function PurchaseOrdersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const roles = useAppSelector((s) => s.auth.user?.roles ?? [])
  const isAA = roles.includes('ROLE_ASSISTANT_COMPTABLE')

  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({ poNumber: '', supplierId: '', totalAmount: '', currency: 'XAF', status: 'OPEN' })

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['purchase-orders', page],
    queryFn: async () => {
      const { data } = await apiClient.get('/purchase-orders', { params: { page, size: 20 } })
      return data.data as { content: PurchaseOrder[]; totalPages: number; totalElements: number }
    },
  })

  const { data: suppliers } = useQuery({
    queryKey: ['active-suppliers'],
    queryFn: async () => {
      const { data } = await apiClient.get('/suppliers', { params: { status: 'ACTIVE', size: 200 } })
      return (data.data?.content ?? []) as Supplier[]
    },
    enabled: showCreate,
  })

  const createMutation = useMutation({
    mutationFn: () => apiClient.post('/purchase-orders', {
      poNumber: form.poNumber,
      supplierId: form.supplierId || null,
      totalAmount: parseFloat(form.totalAmount),
      currency: form.currency,
      status: 'OPEN',
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['purchase-orders'] })
      setShowCreate(false)
      setForm({ poNumber: '', supplierId: '', totalAmount: '', currency: 'XAF', status: 'OPEN' })
    },
  })

  const orders = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  const statusBadge = (s: string) => {
    const map: Record<string, string> = {
      OPEN: 'bg-pos-bg text-pos',
      PARTIALLY_RECEIVED: 'bg-warn-bg text-warn',
      CLOSED: 'bg-ground text-ink-soft',
      CANCELLED: 'bg-crit-bg text-crit',
    }
    return map[s] ?? 'bg-ground text-ink-soft'
  }

  return (
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="space-y-6">
        <PageHeader
          title={t('po.title', 'Purchase Orders')}
          subtitle={t('po.subtitle', 'Manage purchase orders linked to supplier invoices')}
          actions={isAA && (
            <>
              {/* Bulk import is not implemented (no ERP integration, Module 12 out of scope) —
                  the button is honestly disabled instead of pretending a feature exists. */}
              <button
                type="button"
                disabled
                title={t('po.importUnavailable', 'ERP import unavailable — enter manually')}
                aria-disabled="true"
                className="flex items-center gap-2 px-3 py-2 border border-white/30 rounded-[4px] text-sm font-medium text-white/50 cursor-not-allowed"
              >
                <FileSpreadsheet className="w-4 h-4" />
                {t('po.importFile', 'Import')}
              </button>
              <button
                onClick={() => setShowCreate(true)}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 transition-colors"
              >
                <Plus className="w-4 h-4" /> {t('po.create', 'New PO')}
              </button>
            </>
          )}
        />

        {/* Honest note: bulk import is unavailable, manual entry works below */}
        {isAA && (
          <div className="flex items-start gap-3 bg-ground border border-hairline rounded-[4px] p-4">
            <FileSpreadsheet className="w-5 h-5 text-ink-faint shrink-0 mt-0.5" />
            <p className="text-sm font-medium text-ink-soft">{t('po.importUnavailable', 'ERP import unavailable — enter manually')}</p>
          </div>
        )}

        {/* Create form */}
        {showCreate && (
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-4">
            <h2 className="font-semibold text-ink">{t('po.create', 'New Purchase Order')}</h2>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="poNumber" className="block text-sm font-medium text-ink-soft mb-1">{t('po.poNumber', 'PO Number')} *</label>
                <input id="poNumber" value={form.poNumber} onChange={e => setForm(p => ({ ...p, poNumber: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="BC-2026-001" />
              </div>
              <div>
                <label htmlFor="poSupplierId" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.supplier', 'Supplier')}</label>
                <select id="poSupplierId" value={form.supplierId} onChange={e => setForm(p => ({ ...p, supplierId: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                  <option value="">{t('invoice.selectSupplier', '— Select supplier —')}</option>
                  {(suppliers ?? []).map(s => <option key={s.id} value={s.id}>{s.companyName}</option>)}
                </select>
              </div>
              <div>
                <label htmlFor="poTotalAmount" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.amount', 'Total Amount')} *</label>
                <input id="poTotalAmount" type="number" step="0.01" value={form.totalAmount} onChange={e => setForm(p => ({ ...p, totalAmount: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              {/* AUDIT-033 (D4): single-currency system — XAF only. A purchase order in EUR/USD
                  could never match an invoice, which the DTO now restricts to XAF. */}
              <div>
                <label htmlFor="po-currency" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.currency', 'Currency')}</label>
                <input id="po-currency" type="text" value="XAF" readOnly
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft focus:outline-none" />
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2 border-t">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">{t('app.cancel')}</button>
              <button onClick={() => createMutation.mutate()} disabled={!form.poNumber || !form.totalAmount || createMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium disabled:opacity-50">
                {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('app.save', 'Save')}
              </button>
            </div>
          </div>
        )}

        {/* List */}
        <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : isError ? (
            <div className="text-center py-20 space-y-3">
              <p className="text-crit text-sm">{t('app.error')}</p>
              <button onClick={() => refetch()} className="px-4 py-2 text-sm border border-hairline rounded-[4px] hover:bg-ground">{t('app.retry')}</button>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-ground border-b">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('po.poNumber', 'PO Number')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.supplier', 'Supplier')}</th>
                    <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('invoice.amount', 'Total')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.status', 'Status')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.audit.date', 'Created')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {orders.length === 0 ? (
                    <tr><td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td></tr>
                  ) : orders.map(po => (
                    <tr key={po.id} className="hover:bg-ground">
                      <td className="px-4 py-3 num text-xs font-medium text-ink">{po.poNumber}</td>
                      <td className="px-4 py-3 text-ink-soft">{po.supplierName ?? '—'}</td>
                      <td className="px-4 py-3 text-right num">{formatAmount(po.totalAmount)} {po.currency ?? 'XAF'}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${statusBadge(po.status)}`}>{po.status}</span>
                      </td>
                      <td className="px-4 py-3 text-ink-soft text-xs">{formatDate(po.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {totalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t bg-ground">
                  <span className="text-sm text-muted-foreground">{t('pagination.page')} {page + 1} {t('pagination.of')} {totalPages}</span>
                  <div className="flex gap-2">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface">
                      <ChevronLeft className="w-4 h-4" />{t('pagination.previous')}
                    </button>
                    <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface">
                      {t('pagination.next')}<ChevronRight className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </PageRoleGuard>
  )
}
