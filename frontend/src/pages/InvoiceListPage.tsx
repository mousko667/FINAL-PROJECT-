import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { invoiceService, type InvoiceFilters } from '@/services/invoiceService'
import { StatusBadge } from '@/components/ui/StatusBadge'
import type { InvoiceStatus } from '@/types/invoice'
import { Plus, Search, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react'

const ALL_STATUSES: InvoiceStatus[] = [
  'BROUILLON', 'SOUMIS', 'EN_VALIDATION_N1', 'EN_VALIDATION_N2',
  'VALIDE', 'BON_A_PAYER', 'PAYE', 'ARCHIVE', 'REJETE',
]

export default function InvoiceListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [filters, setFilters] = useState<InvoiceFilters>({ page: 0, size: 20 })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['invoices', filters],
    queryFn: () => invoiceService.list(filters),
  })

  const handleFilterChange = (key: keyof InvoiceFilters, value: string) => {
    setFilters((prev) => ({ ...prev, [key]: value || undefined, page: 0 }))
  }

  const invoices = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const currentPage = filters.page ?? 0

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">{t('invoice.title')}</h1>
        <button
          id="btn-new-invoice"
          onClick={() => navigate('/invoices/new')}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('invoice.new')}
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border p-4 flex flex-wrap gap-3">
        {/* Reference search */}
        <div className="flex items-center gap-2 border rounded-lg px-3 py-2 text-sm focus-within:ring-2 focus-within:ring-primary/30 flex-1 min-w-[200px]">
          <Search className="w-4 h-4 text-muted-foreground shrink-0" />
          <input
            id="filter-reference"
            type="text"
            placeholder={t('invoice.reference')}
            className="outline-none w-full bg-transparent text-sm"
            onChange={(e) => handleFilterChange('reference', e.target.value)}
          />
        </div>

        {/* Status filter */}
        <select
          id="filter-status"
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 min-w-[160px]"
          onChange={(e) => handleFilterChange('status', e.target.value)}
        >
          <option value="">{t('app.all')}</option>
          {ALL_STATUSES.map((s) => (
            <option key={s} value={s}>{t(`status.${s}`)}</option>
          ))}
        </select>

        {/* Date range */}
        <input
          id="filter-from-date"
          type="date"
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          onChange={(e) => handleFilterChange('fromDate', e.target.value)}
        />
        <input
          id="filter-to-date"
          type="date"
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          onChange={(e) => handleFilterChange('toDate', e.target.value)}
        />
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading && (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        )}

        {isError && (
          <div className="text-center py-20 text-red-500 text-sm">{t('app.error')}</div>
        )}

        {!isLoading && !isError && (
          <>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier')}</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.issueDate')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.dueDate')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.status')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.department')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td>
                  </tr>
                ) : (
                  invoices.map((invoice) => (
                    <tr
                      key={invoice.id}
                      id={`invoice-row-${invoice.id}`}
                      className="hover:bg-gray-50 cursor-pointer transition-colors"
                      onClick={() => navigate(`/invoices/${invoice.id}`)}
                    >
                      <td className="px-4 py-3 font-medium text-primary">{invoice.referenceNumber}</td>
                      <td className="px-4 py-3 text-gray-700">{invoice.supplierName}</td>
                      <td className="px-4 py-3 text-right font-mono">
                        {invoice.amount.toLocaleString()} {invoice.currency}
                      </td>
                      <td className="px-4 py-3 text-gray-500">{invoice.issueDate}</td>
                      <td className="px-4 py-3 text-gray-500">{invoice.dueDate}</td>
                      <td className="px-4 py-3"><StatusBadge status={invoice.status} /></td>
                      <td className="px-4 py-3 text-gray-500">{invoice.department?.code ?? '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
                <span className="text-sm text-muted-foreground">
                  {t('pagination.page')} {currentPage + 1} {t('pagination.of')} {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    id="btn-prev-page"
                    disabled={currentPage === 0}
                    onClick={() => setFilters((p) => ({ ...p, page: currentPage - 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4" />
                    {t('pagination.previous')}
                  </button>
                  <button
                    id="btn-next-page"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setFilters((p) => ({ ...p, page: currentPage + 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white transition-colors"
                  >
                    {t('pagination.next')}
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
