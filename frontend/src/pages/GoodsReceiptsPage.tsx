import { useState } from 'react'
import { translateApiMessage } from '@/types/apiError'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, Package, Calendar } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatDate } from '@/lib/format'

// AUDIT-028: this type used to declare fields the backend never sent (`supplierName`,
// `receivedDate`, `status`, `notes`). Nobody noticed because the list endpoint returned a
// hardcoded empty array — no row was ever rendered. Now that the list fills up, it is aligned on
// what `GoodsReceiptDTO` actually returns.
interface GoodsReceipt {
  id: string
  grnNumber: string
  purchaseOrderId?: string
  purchaseOrderNumber?: string
  receivedByUsername: string
  receiptDate: string
  items?: Array<{
    id: string
    purchaseOrderItemId?: string
    itemDescription: string
    receivedQuantity: number
  }>
  createdAt: string
}

interface GoodsReceiptDetail {
  id: string
  grnNumber: string
  purchaseOrderId?: string
  purchaseOrderNumber?: string
  receivedByUsername: string
  receiptDate: string
  createdAt: string
  items: Array<{
    id: string
    purchaseOrderItemId?: string
    itemDescription: string
    receivedQuantity: number
  }>
}

interface PO { id: string; poNumber: string; supplierName?: string; totalAmount: number; currency?: string }

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
  const [selectedGrnId, setSelectedGrnId] = useState<string | null>(null)

  const { data: grns, isLoading } = useQuery({
    queryKey: ['goods-receipts'],
    queryFn: async () => {
      // AUDIT-028: the unfiltered branch used to return a hardcoded empty list; it now returns a
      // paginated payload, same shape as /purchase-orders. `size: 200` covers the expected volume
      // without paging controls — see docs/TASKS.md for the real pagination follow-up.
      const { data } = await apiClient.get<{ data: { content: GoodsReceipt[] } }>(
        '/goods-receipts',
        { params: { size: 200 } },
      )
      return data.data?.content ?? []
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

  const { data: grnDetail, isLoading: isLoadingDetail } = useQuery({
    queryKey: ['goods-receipt', selectedGrnId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: GoodsReceiptDetail }>(`/goods-receipts/${selectedGrnId}`)
      return data.data
    },
    enabled: !!selectedGrnId,
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
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="space-y-6">
        <PageHeader
          title={t('grn.title', 'Bons de Réception (GRN)')}
          subtitle={t('grn.subtitle', 'Enregistrez les marchandises reçues liées aux bons de commande')}
          actions={
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 transition-colors"
            >
              <Plus className="w-4 h-4" /> {t('grn.create', 'Nouveau GRN')}
            </button>
          }
        />

        {/* Create form */}
        {showCreate && (
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-4">
            <h2 className="font-semibold text-ink">{t('grn.create', 'Nouveau bon de réception')}</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="grnNumber" className="block text-sm font-medium text-ink-soft mb-1">{t('grn.grnNumber', 'N° GRN')} *</label>
                <input id="grnNumber" value={form.grnNumber} onChange={e => setForm(f => ({ ...f, grnNumber: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              <div>
                <label htmlFor="receivedDate" className="block text-sm font-medium text-ink-soft mb-1">{t('grn.receivedDate', 'Date de réception')} *</label>
                <input id="receivedDate" type="date" value={form.receivedDate} onChange={e => setForm(f => ({ ...f, receivedDate: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
              <div className="md:col-span-2">
                <label htmlFor="grnPurchaseOrderId" className="block text-sm font-medium text-ink-soft mb-1">{t('nav.purchaseOrders', 'Bon de commande lié')}</label>
                <select id="grnPurchaseOrderId" value={form.purchaseOrderId} onChange={e => setForm(f => ({ ...f, purchaseOrderId: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary/30">
                  <option value="">— Sans bon de commande —</option>
                  {(purchaseOrders ?? []).map(po => (
                    <option key={po.id} value={po.id}>{po.poNumber} — {po.supplierName ?? '?'}</option>
                  ))}
                </select>
              </div>
              <div className="md:col-span-2">
                <label htmlFor="grnNotes" className="block text-sm font-medium text-ink-soft mb-1">Notes</label>
                <textarea id="grnNotes" rows={2} value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none" />
              </div>
            </div>

            {/* Items */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-sm font-medium text-ink-soft">{t('invoice.lineItems', 'Articles reçus')}</label>
                <button onClick={addItem} className="text-xs text-primary hover:underline">+ Ajouter une ligne</button>
              </div>
              <div className="space-y-2">
                {form.items.map((item, i) => (
                  <div key={i} className="grid grid-cols-12 gap-2 items-center">
                    <input placeholder="Description" value={item.description} onChange={e => updateItem(i, 'description', e.target.value)}
                      className="col-span-6 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <input type="number" placeholder={t('grn.qtyPlaceholder')} value={item.quantityReceived} onChange={e => updateItem(i, 'quantityReceived', Number(e.target.value))}
                      className="col-span-2 border border-hairline rounded-[4px] px-3 py-2 text-sm text-center focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <input type="number" placeholder="Prix unitaire" value={item.unitPrice} onChange={e => updateItem(i, 'unitPrice', Number(e.target.value))}
                      className="col-span-3 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                    <button onClick={() => removeItem(i)} disabled={form.items.length === 1} className="col-span-1 text-crit hover:text-crit disabled:opacity-30 text-lg leading-none text-center">×</button>
                  </div>
                ))}
              </div>
            </div>

            {createMutation.isError && (
              <p className="text-xs text-crit bg-crit-bg p-2 rounded border border-crit/30">
                {translateApiMessage(createMutation.error, t) ?? t('app.error')}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-2 border-t">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">{t('app.cancel')}</button>
              <button onClick={() => createMutation.mutate()}
                disabled={!form.grnNumber || createMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium disabled:opacity-60">
                {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('app.save', 'Enregistrer')}
              </button>
            </div>
          </div>
        )}

        {/* List */}
        <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
          {isLoading ? (
            <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : !grns?.length ? (
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
              <Package className="w-10 h-10" />
              <p className="text-sm">{t('grn.empty', 'Aucun bon de réception enregistré')}</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-ground border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('grn.grnNumber', 'N° GRN')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('grn.purchaseOrder', 'Bon de commande')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('grn.receivedDate', 'Date de réception')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('grn.receivedBy', 'Réceptionné par')}</th>
                  <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('grn.lineCount', 'Lignes')}</th>
                  <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('app.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {grns.map(grn => (
                  <tr key={grn.id} className="hover:bg-ground">
                    <td className="px-4 py-3 num text-xs font-semibold text-ink">{grn.grnNumber}</td>
                    <td className="px-4 py-3 num text-xs text-ink-soft">{grn.purchaseOrderNumber ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-soft text-xs">
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5" />
                        {formatDate(grn.receiptDate)}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-ink-soft text-xs">{grn.receivedByUsername}</td>
                    <td className="px-4 py-3 text-right num text-xs text-ink-soft">{grn.items?.length ?? 0}</td>
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => setSelectedGrnId(grn.id)} className="text-primary hover:underline text-xs font-medium">
                        {t('app.view', 'Voir')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Detail Modal */}
        {selectedGrnId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="bg-surface rounded-[4px] shadow-lg w-full max-w-2xl max-h-[90vh] flex flex-col">
              <div className="flex items-center justify-between px-6 py-4 border-b border-hairline">
                <h3 className="font-semibold text-lg text-ink">{t('grn.details', 'Détails du bon de réception')}</h3>
                <button onClick={() => setSelectedGrnId(null)} className="text-ink-faint hover:text-ink text-xl leading-none">&times;</button>
              </div>
              <div className="p-6 overflow-y-auto">
                {isLoadingDetail ? (
                  <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
                ) : !grnDetail ? (
                  <div className="text-center py-8 text-ink-soft">{t('app.error')}</div>
                ) : (
                  <div className="space-y-6">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <p className="text-xs text-ink-faint uppercase">{t('grn.grnNumber', 'N° GRN')}</p>
                        <p className="font-medium text-ink mt-1">{grnDetail.grnNumber}</p>
                      </div>
                      <div>
                        <p className="text-xs text-ink-faint uppercase">{t('invoice.purchaseOrder', 'Bon de commande')}</p>
                        <p className="font-medium text-ink mt-1">{grnDetail.purchaseOrderNumber ?? '—'}</p>
                      </div>
                      <div>
                        <p className="text-xs text-ink-faint uppercase">{t('grn.receivedDate', 'Date de réception')}</p>
                        <p className="font-medium text-ink mt-1">{formatDate(grnDetail.receiptDate)}</p>
                      </div>
                      <div>
                        <p className="text-xs text-ink-faint uppercase">{t('reports.recentActivity.changedBy', 'Créé par')}</p>
                        <p className="font-medium text-ink mt-1">{grnDetail.receivedByUsername}</p>
                      </div>
                    </div>
                    <div>
                      <h4 className="font-medium text-ink mb-3">{t('grn.items', 'Articles')}</h4>
                      {/* AUDIT-019: overflow-x-auto, sinon overflow-hidden coupe les
                          colonnes sur mobile sans permettre de les atteindre. */}
                      <div className="border border-hairline rounded-[4px] overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead className="bg-ground border-b border-hairline">
                            <tr>
                              <th className="text-left px-4 py-2 font-medium text-ink-soft">{t('invoice.description', 'Description')}</th>
                              <th className="text-right px-4 py-2 font-medium text-ink-soft">{t('invoice.quantity', 'Qté')}</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-hairline">
                            {grnDetail.items?.map(item => (
                              <tr key={item.id} className="hover:bg-ground">
                                <td className="px-4 py-2 text-ink">{item.itemDescription}</td>
                                <td className="px-4 py-2 text-right num text-ink">{item.receivedQuantity}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                )}
              </div>
              <div className="px-6 py-4 border-t border-hairline flex justify-end">
                <button onClick={() => setSelectedGrnId(null)} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
                  {t('grn.close', 'Fermer')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </PageRoleGuard>
  )
}
