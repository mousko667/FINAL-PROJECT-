import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoiceService } from '@/services/invoiceService'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { InvoiceTimeline } from '@/components/invoice/InvoiceTimeline'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
import { Loader2, ArrowLeft, Download } from 'lucide-react'

export default function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: invoice, isLoading, isError } = useQuery({
    queryKey: ['invoice', id],
    queryFn: () => invoiceService.getById(id!),
    enabled: !!id,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (isError || !invoice) {
    return (
      <div className="text-center py-20">
        <p className="text-red-500 mb-4">{t('app.error')}</p>
        <button onClick={() => navigate('/invoices')} className="text-primary underline text-sm">
          {t('pagination.previous')}
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            id="btn-back"
            onClick={() => navigate('/invoices')}
            className="p-2 rounded-lg hover:bg-gray-100 text-gray-500 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{invoice.referenceNumber}</h1>
            <p className="text-sm text-muted-foreground">{invoice.supplierName}</p>
          </div>
        </div>
        <StatusBadge status={invoice.status} className="text-sm px-3 py-1" />
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Main details */}
        <div className="col-span-2 space-y-6">
          {/* Invoice summary card */}
          <div className="bg-white rounded-xl border p-6">
            <h2 className="font-semibold text-gray-800 mb-4">{t('invoice.details')}</h2>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
              {[
                { label: t('invoice.amount'), value: `${invoice.amount.toLocaleString()} ${invoice.currency}` },
                { label: t('invoice.issueDate'), value: invoice.issueDate },
                { label: t('invoice.dueDate'), value: invoice.dueDate },
                { label: t('invoice.department'), value: invoice.department?.name ?? '—' },
              ].map(({ label, value }) => (
                <div key={label}>
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="font-medium text-gray-800 mt-0.5">{value}</dd>
                </div>
              ))}
              {invoice.description && (
                <div className="col-span-2">
                  <dt className="text-muted-foreground">{t('invoice.description')}</dt>
                  <dd className="mt-0.5 text-gray-700">{invoice.description}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Line items */}
          {invoice.lineItems && invoice.lineItems.length > 0 && (
            <div className="bg-white rounded-xl border overflow-hidden">
              <div className="px-5 py-4 border-b font-semibold text-gray-800">{t('invoice.lineItems')}</div>
              <table className="w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-5 py-2 text-gray-500 font-medium">Description</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">Qté</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">P.U.</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {invoice.lineItems.map((li, i) => (
                    <tr key={i}>
                      <td className="px-5 py-2">{li.description}</td>
                      <td className="px-5 py-2 text-right">{li.quantity}</td>
                      <td className="px-5 py-2 text-right font-mono">{li.unitPrice.toFixed(2)}</td>
                      <td className="px-5 py-2 text-right font-mono font-medium">{li.totalPrice.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Documents */}
          {invoice.documents && invoice.documents.length > 0 && (
            <div className="bg-white rounded-xl border p-5">
              <h2 className="font-semibold text-gray-800 mb-3">{t('invoice.documents')}</h2>
              <ul className="space-y-2">
                {invoice.documents.map((doc) => (
                  <li key={doc.id} className="flex items-center justify-between text-sm px-3 py-2.5 bg-gray-50 rounded-lg border">
                    <span className="font-medium text-gray-700 truncate">{doc.fileName}</span>
                    <div className="flex items-center gap-3 ml-2 shrink-0">
                      <span className="text-xs text-muted-foreground">{(doc.fileSize / 1024).toFixed(1)} KB</span>
                      {doc.downloadUrl && (
                        <a href={doc.downloadUrl} download className="text-primary hover:text-primary/80">
                          <Download className="w-4 h-4" />
                        </a>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Sidebar: actions + timeline */}
        <div className="space-y-6">
          <InvoiceActionPanel invoice={invoice} />
          <InvoiceTimeline invoice={invoice} />
        </div>
      </div>
    </div>
  )
}
