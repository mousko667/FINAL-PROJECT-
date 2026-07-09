import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { UploadCloud, Loader2, CheckCircle2, XCircle, FileText } from 'lucide-react'
import { invoiceService, type ImportResult } from '@/services/invoiceService'

interface ImportInvoicesModalProps {
  onClose: () => void
  /** Called once at least one invoice was created, so the caller can refresh its list. */
  onImported: () => void
}

/**
 * B8 (M3) — bulk multi-invoice import. Lets an Assistant Comptable upload a CSV (one row per
 * invoice) or an XML document (several <invoice> elements) and shows a best-effort, per-line
 * result. Distinct from the single-invoice document upload.
 */
export function ImportInvoicesModal({ onClose, onImported }: ImportInvoicesModalProps) {
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [departmentCode, setDepartmentCode] = useState('')
  const [result, setResult] = useState<ImportResult | null>(null)

  const mutation = useMutation({
    mutationFn: () => invoiceService.importInvoices(file!, departmentCode.trim() || undefined),
    onSuccess: (res) => {
      setResult(res)
      if (res.created > 0) onImported()
    },
  })

  const errorMessage = mutation.isError
    ? t('invoice.import.unreadable_file', 'Le fichier est illisible ou dans un format non supporté.')
    : null

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div
        className="bg-surface rounded-2xl shadow-2xl w-full max-w-lg max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <h2 className="text-sm font-semibold text-ink">
            {t('invoice.import.title', 'Importer des factures')}
          </h2>
          <button onClick={onClose} className="text-ink-faint hover:text-ink-soft text-xl leading-none">×</button>
        </div>

        <div className="flex-1 overflow-auto p-5 space-y-4">
          <p className="text-xs text-ink-soft">
            {t('invoice.import.hint', 'Fichier CSV (une facture par ligne) ou XML (plusieurs éléments <invoice>). Chaque ligne est traitée indépendamment.')}
          </p>

          {!result && (
            <>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="w-full border-2 border-dashed rounded-[4px] py-8 flex flex-col items-center gap-2 text-ink-soft hover:border-primary hover:text-primary transition-colors"
              >
                <UploadCloud className="w-8 h-8" />
                <span className="text-sm font-medium">
                  {file ? file.name : t('invoice.import.choose_file', 'Choisir un fichier CSV ou XML')}
                </span>
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".csv,.xml,text/csv,application/xml,text/xml"
                className="hidden"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null)
                  mutation.reset()
                }}
              />

              <div>
                <label className="block text-xs font-medium text-ink-soft mb-1">
                  {t('invoice.import.department_code', 'Code département (optionnel)')}
                </label>
                <input
                  type="text"
                  value={departmentCode}
                  onChange={(e) => setDepartmentCode(e.target.value)}
                  placeholder={t('invoice.import.department_code_placeholder', 'ex. INFO')}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>

              {errorMessage && (
                <div className="text-sm text-crit bg-crit-bg border border-crit/30 rounded-[4px] px-3 py-2">
                  {errorMessage}
                </div>
              )}
            </>
          )}

          {result && (
            <div className="space-y-3">
              <div className="flex gap-3 text-sm">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-ground text-ink-soft">
                  <FileText className="w-4 h-4" /> {t('invoice.import.total', 'Total')}: {result.total}
                </span>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-pos-bg text-pos">
                  <CheckCircle2 className="w-4 h-4" /> {t('invoice.import.created', 'Créées')}: {result.created}
                </span>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-crit-bg text-crit">
                  <XCircle className="w-4 h-4" /> {t('invoice.import.failed', 'Échecs')}: {result.failed}
                </span>
              </div>

              <div className="border border-hairline rounded-[4px] overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="bg-ground border-b text-ink-soft">
                    <tr>
                      <th className="text-left px-3 py-2 font-medium">{t('invoice.import.line', 'Ligne')}</th>
                      <th className="text-left px-3 py-2 font-medium">{t('invoice.import.outcome', 'Résultat')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {result.results.map((r) => (
                      <tr key={r.line}>
                        <td className="px-3 py-2 text-ink-soft">{r.line}</td>
                        <td className="px-3 py-2">
                          {r.success ? (
                            <span className="inline-flex items-center gap-1.5 text-pos">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              {r.reference ?? t('invoice.import.ok', 'Créée')}
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 text-crit">
                              <XCircle className="w-3.5 h-3.5" />
                              {r.error ?? t('app.error', 'Erreur')}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 px-5 py-3 border-t">
          {!result ? (
            <>
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm border border-hairline rounded-[4px] text-ink-soft hover:bg-ground transition-colors"
              >
                {t('app.cancel', 'Annuler')}
              </button>
              <button
                onClick={() => mutation.mutate()}
                disabled={!file || mutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-40 transition-colors"
              >
                {mutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('invoice.import.submit', 'Importer')}
              </button>
            </>
          ) : (
            <button
              onClick={onClose}
              className="px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 transition-colors"
            >
              {t('app.close', 'Fermer')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
