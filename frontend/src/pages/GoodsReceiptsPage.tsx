import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, Package, CheckCircle, Calendar } from 'lucide-react'
import { formatDate } from '@/lib/format'

interface GoodsReceipt {
  id: string
  grnNumber: string
  purchaseOrderId?: string
  supplierName?: string
  receivedDate: string
  status: string
  notes?: string
  items?: Array<{ description: string; quantityReceived: number; unitPrice: number }>
  createdAt: string
}

interface PO { id: string; poNumber: string; supplierName?: string; totalAmount: number; currency?: string }

const STATUS_COLORS: Record<string, string> = {
  RECEIVED:         'bg-green-100 text-green-700',
  PARTIALLY_RECEIVED:'bg-yellow-100 text-yellow-700',
  PENDING:          'bg-gray-100 text-gray-600',
  REJECTED:         'bg-red-100 text-red-700',
}

export default function GoodsReceiptsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({
    grnNumber: `GRN-${new Date().getFullYear()}-${Date.now().toString().slice(-4)}`,
    purchaseOrderId: '',
    receivedDate: new Date().toISOString().slice(0, 10),
    notes: '',
    items: [{ description: '', quantityReceived: 1, unitPrice: 0 }],
  })

  const { data: grns, isLoading } = useQuery({
    queryKey: ['goods-receipts'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: GoodsReceipt[] }>('/goods-receipts')
      return Array.isArray(data.data) ? data.data : []
    },
  })

  const { data: purchaseOrders } = useQuery({
    queryKey: ['po-list'],
    queryFn: async () => {
      const { data } = await apiClient.get('/purchase-orders', { params: { size: 200, status: 'OPEN' } })
      return (Array.isArray(data.data) ? data.data : data.data?.content ?? []) as PO[]
    },
    enabled: showCreate,
  })

  const createMutation = useMutation({
    mutationFn: () => apiClient.post('/goods-receipts', {
      grnNumber: form.grnNumber,
      purchaseOrderId: form.purchaseOrderId || null,
      receivedDate: form.receivedDate,
      notes: form.notes || null,
      items: form.items.filter(i => i.description),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goods-receipts'] })
      setShowCreate(false)
      setForm(f => ({ ...f, grnNumber: `GRN-${new Date().getFullYear()}-${Date.now().toString().slice(-4)}`, notes: '', purchaseOrderId: '', items: [{ description: '', quantityReceived: 1, unitPrice: 0 }] }))
    },
  })

  const addItem = () => setForm(f => ({ ...f, items: [...f.items, { description: '', quantityReceived: 1, unitPrice: 0 }] }))
  const removeItem = (i: number) => setForm(f => ({ ...f, items: f.items.filter((_, idx) => idx !== i) }))
  const updateItem = (i: number, field: string, value: string | number) =>
    setForm(f => ({ ...f, items: f.items.map((item, idx) => idx === i ? { ...item, [field]: value } : item) }))

  return (
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF', 'ROLE_ADMIN']}>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{t('grn.title', 'Bons de Réception (GRN)')}</h1>
            <p className="text-sm text-gray-500 mt-0.5">{t('grn.subtitle', 'Enregistrez les marchandises reçues liées aux bons de commande')}</p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" /> {t('grn.create', 'Nouveau GRN')}
          </button>
        </div>

        {/* Create form */}
        {showCreate && (
          <div className="bg-white rounded-xl border p-6 space-y-4">
            <h2 className="font-semibold text-gray-900">{t('grn.create', 'Nouveau bon de réception')}</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('grn.grnNumber', 'N° GRN')} *</label>
                <input value={form.grnNumber} onChange={e => setForm(f => ({ ...f, grnNumber: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('grn.receivedDate', 'Date de réception')} *</label>
                <input type="date" value={form.receivedDate} onChange={e => setForm(f => ({ ...f, receivedDate: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('nav.purchaseOrders', 'Bon de commande lié')}</label>
                <select value={form.purchaseOrderId} onChange={e => setForm(f => ({ ...f, purchaseOrderId: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
                  <option value="">— Sans bon de commande —</option>
                  {(purchaseOrders ?? []).map(po => (
                    <option key={po.id} value={po.id}>{po.poNumber} — {po.supplierName ?? '?'}</option>
                  ))}
                </select>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea rows={2} value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none" />
              </div>
            </div>

            {/* Items */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-sm font-medium text-gray-700">{t('invoice.lineItems', 'Articles reçus')}</label>
                <button onClick={addItem} className="text-xs text-primary hover:underline">+ Ajouter une ligne</button>
              </div>
              <div className="space-y-2">
                {form.items.map((item, i) => (
                  <div key={i} className="grid grid-cols-12 gap-2 items-center">
                    <input placeholder="Description" value={item.description} onChange={e => updateItem(i, 'description', e.target.value)}
                      className="col-span-6 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <input type="number" placeholder="Qté" value={item.quantityReceived} onChange={e => updateItem(i, 'quantityReceived', Number(e.target.value))}
                      className="col-span-2 border rounded-lg px-3 py-2 text-sm text-center focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <input type="number" placeholder="Prix unitaire" value={item.unitPrice} onChange={e => updateItem(i, 'unitPrice', Number(e.target.value))}
                      className="col-span-3 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <button onClick={() => removeItem(i)} disabled={form.items.length === 1} className="col-span-1 text-red-400 hover:text-red-600 disabled:opacity-30 text-lg leading-none text-center">×</button>
                  </div>
                ))}
              </div>
            </div>

            {createMutation.isError && (
              <p className="text-xs text-red-600 bg-red-50 p-2 rounded border border-red-200">
                {(createMutation.error as any)?.response?.data?.message ?? t('app.error')}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-2 border-t">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">{t('app.cancel')}</button>
              <button onClick={() => createMutation.mutate()}
                disabled={!form.grnNumber || createMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium disabled:opacity-60">
                {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('app.save', 'Enregistrer')}
              </button>
            </div>
          </div>
        )}

        {/* List */}
        <div className="bg-white rounded-xl border overflow-hidden">
          {isLoading ? (
            <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : !grns?.length ? (
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-gray-400">
              <Package className="w-10 h-10" />
              <p className="text-sm">{t('grn.empty', 'Aucun bon de réception enregistré')}</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('grn.grnNumber', 'N° GRN')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier', 'Fournisseur')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('grn.receivedDate', 'Date de réception')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.status', 'Statut')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {grns.map(grn => (
                  <tr key={grn.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-gray-900">{grn.grnNumber}</td>
                    <td className="px-4 py-3 text-gray-700">{grn.supplierName ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5" />
                        {formatDate(grn.receivedDate)}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_COLORS[grn.status] ?? 'bg-gray-100 text-gray-600'}`}>
                        {grn.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs truncate max-w-xs">{grn.notes ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </PageRoleGuard>
  )
}
