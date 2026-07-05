import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Panel } from "@/components/ui/Panel"
import {  Loader2, ListChecks, Save, CheckCircle  } from 'lucide-react'

interface ChecklistItem {
  templateItemId: string
  label: string
  required: boolean
  displayOrder: number
  checked: boolean
  note?: string | null
}
interface InvoiceChecklist {
  templateId: string | null
  templateName: string | null
  answered: boolean
  respondedAt: string | null
  items: ChecklistItem[]
}

/**
 * Validation checklist shown on the invoice review screen (B1). Loads the applicable template
 * (department-scoped or global) merged with any saved answers, lets the validator tick items and
 * add notes, and persists them. Non-blocking — it never gates the workflow actions.
 */
export function ValidationChecklist({ invoiceId }: { invoiceId: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [items, setItems] = useState<ChecklistItem[]>([])
  const [justSaved, setJustSaved] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['invoice-checklist', invoiceId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: InvoiceChecklist }>(`/invoices/${invoiceId}/checklist`)
      return data.data
    },
  })

  useEffect(() => {
    if (data?.items) setItems(data.items.map(i => ({ ...i })))
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => apiClient.post(`/invoices/${invoiceId}/checklist`, {
      templateId: data!.templateId,
      items: items.map(i => ({ templateItemId: i.templateItemId, checked: i.checked, note: i.note || null })),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice-checklist', invoiceId] })
      setJustSaved(true)
      setTimeout(() => setJustSaved(false), 3000)
    },
  })

  if (isLoading) {
    return (
      <div className="bg-surface rounded-xl border border-hairline p-6 flex items-center justify-center">
        <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // No template applies to this invoice's department → nothing to show.
  if (!data || !data.templateId) return null

  const setItem = (idx: number, patch: Partial<ChecklistItem>) =>
    setItems(prev => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)))

  const requiredDone = items.filter(i => i.required).every(i => i.checked)

  return (
    <div className="bg-surface rounded-xl border border-hairline p-6">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <ListChecks className="w-5 h-5 text-ink-faint" />
          <h2 className="font-semibold text-ink">{t('checklist.validationTitle', 'Validation Checklist')}</h2>
        </div>
        {data.templateName && <span className="text-xs text-ink-faint">{data.templateName}</span>}
      </div>

      <div className="space-y-3">
        {items.map((it, idx) => (
          <div key={it.templateItemId} className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={it.checked}
              onChange={e => setItem(idx, { checked: e.target.checked })}
              className="mt-1"
            />
            <div className="flex-1">
              <label className="text-sm text-ink">
                {it.label}
                {it.required && <span className="ml-1 text-red-500" title={t('checklist.required', 'Required')}>*</span>}
              </label>
              <input
                value={it.note ?? ''}
                onChange={e => setItem(idx, { note: e.target.value })}
                placeholder={t('checklist.notePlaceholder', 'Optional note…')}
                className="mt-1 w-full border rounded px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>
          </div>
        ))}
      </div>

      {!requiredDone && (
        <p className="text-xs text-amber-600 mt-3">{t('checklist.requiredHint', 'Some required items are not yet checked (informational — does not block validation).')}</p>
      )}

      <div className="flex items-center justify-end gap-3 mt-4 pt-3 border-t">
        {justSaved && (
          <span className="text-xs text-pos flex items-center gap-1">
            <CheckCircle className="w-3.5 h-3.5" /> {t('checklist.saved', 'Saved')}
          </span>
        )}
        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
        >
          {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
          {t('checklist.saveAnswers', 'Save checklist')}
        </button>
      </div>
    </div>
  )
}
