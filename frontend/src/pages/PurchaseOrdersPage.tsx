import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { useAppSelector } from '@/store/hooks'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, ChevronLeft, ChevronRight, Upload, FileSpreadsheet } from 'lucide-react'

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
  const fileRef = useRef<HTMLInputElement>(null)
  const [importError, setImportError] = useState<string | null>(null)

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
      setForm({ poNumber: '', supplierId: '', totalAmount: '', currency: 'XOF', status: 'OPEN' })
    },
  })

  const orders = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  const statusBadge = (s: string) => {
    const map: Record<string, string> = {
      OPEN: 'bg-green-100 text-green-700',
      PARTIALLY_RECEIVED: 'bg-yellow-100 text-yellow-700',
      CLOSED: 'bg-gray-100 text-gray-500',
      CANCELLED: 'bg-red-100 text-red-700',
    }
    return map[s] ?? 'bg-gray-100 text-gray-600'
  }

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{t('po.title', 'Purchase Orders')}</h1>
            <p className="text-sm text-gray-500">{t('po.subtitle', 'Manage purchase orders linked to supplier invoices')}</p>
          </div>
          {isAA && (
            <div className="flex items-center gap-2">
              {/* File import */}
              <label className="cursor-pointer">
                <input
                  ref={fileRef}
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  className="hidden"
                  onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (!file) return
                    setImportError(`File "${file.name}" selected. Bulk import via ERP integration is in progress — manually enter the PO details below for now.`)
                    if (fileRef.current) fileRef.current.value = ''
                    setShowCreate(true)
                  }}
                />
                <span className="flex items-center gap-2 px-3 py-2 border rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors cursor-pointer">
                  <FileSpreadsheet className="w-4 h-4 text-green-600" />
                  {t('po.importFile', 'Import')}
                </span>
              </label>
              <button
                onClick={() => setShowCreate(true)}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
              >
                <Plus className="w-4 h-4" /> {t('po.create', 'New PO')}
              </button>
            </div>
          )}
        </div>

        {/* Import note */}
        {importError && (
          <div className="flex items-start gap-3 bg-blue-50 border border-blue-200 rounded-xl p-4">
            <FileSpreadsheet className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-blue-800">{importError}</p>
              <p className="text-xs text-blue-600 mt-0.5">{t('po.importHint')}</p>
            </div>
            <button onClick={() => setImportError(null)} className="ml-auto text-blue-400 hover:text-blue-600 text-lg leading-none shrink-0">×</button>
          </div>
        )}

        {/* Create form */}
        {showCreate && (
          <div className="bg-white rounded-xl border p-6 space-y-4">
            <h2 className="font-semibold text-gray-800">{t('po.create', 'New Purchase Order')}</h2>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('po.poNumber', 'PO Number')} *</label>
                <input value={form.poNumber} onChange={e => setForm(p => ({ ...p, poNumber: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  placeholder="BC-2026-001" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.supplier', 'Supplier')}</label>
                <select value={form.supplierId} onChange={e => setForm(p => ({ ...p, supplierId: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                  <option value="">{t('invoice.selectSupplier', '— Select supplier —')}</option>
                  {(suppliers ?? []).map(s => <option key={s.id} value={s.id}>{s.companyName}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.amount', 'Total Amount')} *</label>
                <input type="number" step="0.01" value={form.totalAmount} onChange={e => setForm(p => ({ ...p, totalAmount: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.currency', 'Currency')}</label>
                <select value={form.currency} onChange={e => setForm(p => ({ ...p, currency: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                  <option value="XOF">XOF</option>
                  <option value="EUR">EUR</option>
                  <option value="USD">USD</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2 border-t">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">{t('app.cancel')}</button>
              <button onClick={() => createMutation.mutate()} disabled={!form.poNumber || !form.totalAmount || createMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium disabled:opacity-50">
                {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('app.save', 'Save')}
              </button>
            </div>
          </div>
        )}

        {/* List */}
        <div className="bg-white rounded-xl border overflow-hidden">
          {isLoading ? (
            <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : isError ? (
            <div className="text-center py-20 space-y-3">
              <p className="text-red-500 text-sm">{t('app.error')}</p>
              <button onClick={() => refetch()} className="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">{t('app.retry')}</button>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('po.poNumber', 'PO Number')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier', 'Supplier')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount', 'Total')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.status', 'Status')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.date', 'Created')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {orders.length === 0 ? (
                    <tr><td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td></tr>
                  ) : orders.map(po => (
                    <tr key={po.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-xs font-medium text-gray-900">{po.poNumber}</td>
                      <td className="px-4 py-3 text-gray-700">{po.supplierName ?? '—'}</td>
                      <td className="px-4 py-3 text-right font-mono">{Number(po.totalAmount).toLocaleString()} {po.currency ?? 'XOF'}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${statusBadge(po.status)}`}>{po.status}</span>
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{new Date(po.createdAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {totalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
                  <span className="text-sm text-muted-foreground">{t('pagination.page')} {page + 1} {t('pagination.of')} {totalPages}</span>
                  <div className="flex gap-2">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white">
                      <ChevronLeft className="w-4 h-4" />{t('pagination.previous')}
                    </button>
                    <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white">
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
