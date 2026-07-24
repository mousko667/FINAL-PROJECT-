import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { FileText, MessageSquare, Plus, Trash2, Loader2 } from 'lucide-react'
import { formatDateTime } from '@/lib/format'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { notifyApiError } from '@/components/ErrorToaster'

interface Contract {
  id: string; reference: string; title: string
  startDate: string | null; endDate: string | null; status: string; notes: string | null
}
interface Communication {
  id: string; channel: string; subject: string; body: string | null; loggedAt: string
}

/** M8 — supplier contracts + communication log (read for ADMIN/AA/DAF, write for ADMIN/AA). */
export function SupplierRelationship({ supplierId, canEdit }: { supplierId: string; canEdit: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const inputCls = 'w-full border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'

  const { data: contracts = [] } = useQuery<Contract[]>({
    queryKey: ['supplier-contracts', supplierId],
    queryFn: async () => (await apiClient.get<{ data: Contract[] }>(`/suppliers/${supplierId}/contracts`)).data.data ?? [],
  })
  const { data: comms = [] } = useQuery<Communication[]>({
    queryKey: ['supplier-comms', supplierId],
    queryFn: async () => (await apiClient.get<{ data: Communication[] }>(`/suppliers/${supplierId}/communications`)).data.data ?? [],
  })

  const [cRef, setCRef] = useState(''); const [cTitle, setCTitle] = useState('')
  const [cStart, setCStart] = useState(''); const [cEnd, setCEnd] = useState('')
  const [mChannel, setMChannel] = useState('NOTE'); const [mSubject, setMSubject] = useState(''); const [mBody, setMBody] = useState('')
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const addContract = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.post(`/suppliers/${supplierId}/contracts`, {
      reference: cRef, title: cTitle, startDate: cStart || null, endDate: cEnd || null, status: 'ACTIVE' }),
    onSuccess: () => { setCRef(''); setCTitle(''); setCStart(''); setCEnd(''); queryClient.invalidateQueries({ queryKey: ['supplier-contracts', supplierId] }) },
  })
  const delContract = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: (id: string) => apiClient.delete(`/suppliers/${supplierId}/contracts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['supplier-contracts', supplierId] })
      setDeleteTargetId(null)
    },
  })
  const addComm = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.post(`/suppliers/${supplierId}/communications`, { channel: mChannel, subject: mSubject, body: mBody }),
    onSuccess: () => { setMSubject(''); setMBody(''); setMChannel('NOTE'); queryClient.invalidateQueries({ queryKey: ['supplier-comms', supplierId] }) },
  })

  return (
    <div className="space-y-6">
      {/* Contracts */}
      <div className="bg-surface rounded-[4px] border border-hairline p-5">
        <div className="flex items-center gap-2 mb-3">
          <FileText className="w-5 h-5 text-primary" />
          <h3 className="font-semibold text-ink">{t('supplier.contracts.title', 'Contrats & accords')}</h3>
        </div>
        {canEdit && (
          <div className="grid grid-cols-1 md:grid-cols-5 gap-2 mb-4">
            <input value={cRef} onChange={e => setCRef(e.target.value)} placeholder={t('supplier.contracts.reference', 'Référence')} className={inputCls} />
            <input value={cTitle} onChange={e => setCTitle(e.target.value)} placeholder={t('supplier.contracts.titleField', 'Intitulé')} className={inputCls} />
            <input type="date" value={cStart} onChange={e => setCStart(e.target.value)} className={inputCls} />
            <input type="date" value={cEnd} onChange={e => setCEnd(e.target.value)} className={inputCls} />
            <button onClick={() => addContract.mutate()} disabled={!cRef || !cTitle || addContract.isPending}
              className="inline-flex items-center justify-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
              {addContract.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}{t('app.add', 'Ajouter')}
            </button>
          </div>
        )}
        {contracts.length === 0 ? (
          <p className="text-sm text-ink-faint py-2">{t('supplier.contracts.none', 'Aucun contrat.')}</p>
        ) : (
          <ul className="divide-y">
            {contracts.map(c => (
              <li key={c.id} className="flex items-center justify-between py-2 text-sm">
                <div><span className="font-medium text-ink">{c.reference}</span> — {c.title}
                  <span className="text-ink-faint ml-2">{c.startDate ?? '?'} → {c.endDate ?? '?'} · {c.status}</span></div>
                {canEdit && <button onClick={() => setDeleteTargetId(c.id)} className="text-ink-faint hover:text-crit"><Trash2 className="w-4 h-4" /></button>}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Communication log */}
      <div className="bg-surface rounded-[4px] border border-hairline p-5">
        <div className="flex items-center gap-2 mb-3">
          <MessageSquare className="w-5 h-5 text-primary" />
          <h3 className="font-semibold text-ink">{t('supplier.comms.title', 'Journal de communication')}</h3>
        </div>
        {canEdit && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-2 mb-4">
            <select value={mChannel} onChange={e => setMChannel(e.target.value)} className={inputCls}>
              <option value="NOTE">NOTE</option><option value="EMAIL">EMAIL</option>
              <option value="PHONE">PHONE</option><option value="MEETING">MEETING</option>
            </select>
            <input value={mSubject} onChange={e => setMSubject(e.target.value)} placeholder={t('supplier.comms.subject', 'Sujet')} className={`${inputCls} md:col-span-2`} />
            <button onClick={() => addComm.mutate()} disabled={!mSubject || addComm.isPending}
              className="inline-flex items-center justify-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
              {addComm.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}{t('app.add', 'Ajouter')}
            </button>
            <textarea value={mBody} onChange={e => setMBody(e.target.value)} placeholder={t('supplier.comms.body', 'Détails (facultatif)')} rows={2} className={`${inputCls} md:col-span-4`} />
          </div>
        )}
        {comms.length === 0 ? (
          <p className="text-sm text-ink-faint py-2">{t('supplier.comms.none', 'Aucune communication.')}</p>
        ) : (
          <ul className="divide-y">
            {comms.map(c => (
              <li key={c.id} className="py-2 text-sm">
                <div className="flex items-center gap-2">
                  <span className="text-xs num bg-ground text-ink-soft px-1.5 py-0.5 rounded">{c.channel}</span>
                  <span className="font-medium text-ink">{c.subject}</span>
                  <span className="text-ink-faint ml-auto text-xs">{formatDateTime(c.loggedAt)}</span>
                </div>
                {c.body && <p className="text-ink-soft mt-0.5">{c.body}</p>}
              </li>
            ))}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={deleteTargetId !== null}
        title={t('supplier.contracts.deleteConfirmTitle', 'Delete this contract?')}
        message={t('supplier.contracts.deleteConfirmBody', 'This action is permanent.')}
        variant="danger"
        onConfirm={() => { if (deleteTargetId) delContract.mutate(deleteTargetId) }}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  )
}
