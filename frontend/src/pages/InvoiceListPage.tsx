import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import { invoiceService, type InvoiceFilters } from '@/services/invoiceService'
import { StatusBadge } from '@/components/ui/StatusBadge'
import type { InvoiceStatus } from '@/types/invoice'
import { Plus, Search, ChevronLeft, ChevronRight, Loader2, Archive, Lock } from 'lucide-react'

const ALL_STATUSES: InvoiceStatus[] = [
  'BROUILLON', 'SOUMIS', 'EN_VALIDATION_N1', 'EN_VALIDATION_N2',
  'VALIDE', 'BON_A_PAYER', 'PAYE', 'ARCHIVE', 'REJETE',
]

type TabView = 'active' | 'archive' | 'all'

const matchingBadge: Record<string, string> = {
  MATCHED:   'bg-green-100 text-green-700',
  PARTIAL:   'bg-yellow-100 text-yellow-700',
  MISMATCH:  'bg-red-100 text-red-700',
  OVERRIDDEN:'bg-orange-100 text-orange-700',
}

export default function InvoiceListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const user = useAppSelector((s) => s.auth.user)
  const roles = user?.roles ?? []
  const isAA    = roles.includes('ROLE_ASSISTANT_COMPTABLE')
  const isDaf   = roles.includes('ROLE_DAF')
  const isAdmin = roles.includes('ROLE_ADMIN')
  const isValidator = roles.some(r => r.startsWith('ROLE_VALIDATEUR_'))
  // Validators and DAF are scoped to their department by default
  const userDeptId = (isValidator) ? user?.departmentId : undefined

  const [tab, setTab] = useState<TabView>('active')
  const [filters, setFilters] = useState<InvoiceFilters>({ page: 0, size: 20 })

  // Compute effective status filter from tab + explicit status filter
  const effectiveStatus: InvoiceStatus | undefined =
    tab === 'archive' ? 'ARCHIVE' :
    tab === 'active' ? (filters.status === 'ARCHIVE' ? undefined : filters.status) :
    filters.status

  const effectiveFilters: InvoiceFilters = {
    ...filters,
    status: effectiveStatus,
    // Validators automatically see only their department's invoices
    departmentId: filters.departmentId ?? userDeptId,
  }

  const { data, isLoading, isError } = useQuery({
    queryKey: ['invoices', effectiveFilters],
    queryFn: () => invoiceService.list(effectiveFilters),
  })

  const handleFilterChange = (key: keyof InvoiceFilters, value: string) => {
    setFilters((prev) => ({ ...prev, [key]: value || undefined, page: 0 }))
  }

  const invoices = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const currentPage = filters.page ?? 0

  const tabClass = (active: boolean) =>
    `flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
      active ? 'border-primary text-primary' : 'border-transparent text-gray-500 hover:text-gray-700'
    }`

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">{t('invoice.title')}</h1>
        {isAA && (
          <button
            id="btn-new-invoice"
            onClick={() => navigate('/invoices/new')}
            title={t('invoice.newTooltip')}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('invoice.new')}
          </button>
        )}
      </div>

      {/* Department scope notice for validators */}
      {isValidator && userDeptId && (
        <div className="text-xs text-blue-700 bg-blue-50 border border-blue-200 rounded-lg px-4 py-2">
          {t('invoice.deptScopeNote', 'Showing invoices from your department only. Use the All tab to browse without restriction.')}
        </div>
      )}

      {/* Tab bar */}
      <div className="flex border-b bg-white rounded-t-xl px-4 gap-1">
        <button className={tabClass(tab === 'active')} onClick={() => setTab('active')}>
          {t('invoice.tabActive', 'Active')}
        </button>
        <button className={tabClass(tab === 'archive')} onClick={() => setTab('archive')}>
          <Archive className="w-4 h-4" />
          {t('invoice.tabArchive', 'Archive')}
        </button>
        <button className={tabClass(tab === 'all')} onClick={() => setTab('all')}>
          {t('invoice.tabAll', 'All')}
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white border border-t-0 rounded-b-xl p-4 flex flex-wrap gap-3 -mt-6 pt-4">
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

        {/* Status filter — only shown in 'all' tab */}
        {tab === 'all' && (
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
        )}

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
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('matching.columnHeader', 'Matching')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.department')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td>
                  </tr>
                ) : (
                  invoices.map((invoice) => (
                    <tr
                      key={invoice.id}
                      id={`invoice-row-${invoice.id}`}
                      className="hover:bg-gray-50 cursor-pointer transition-colors"
                      onClick={() => navigate(`/invoices/${invoice.id}`)}
                    >
                      <td className="px-4 py-3 font-medium text-primary">
                        <span className="inline-flex items-center gap-1.5">
                          {invoice.dataSensitivity === 'CONFIDENTIAL' && (
                            <Lock className="w-3.5 h-3.5 text-red-600" aria-label={t('sensitivity.CONFIDENTIAL', 'Confidential')} />
                          )}
                          {invoice.referenceNumber}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-700">{invoice.supplierName}</td>
                      <td className="px-4 py-3 text-right font-mono">
                        {invoice.amount.toLocaleString()} {invoice.currency}
                      </td>
                      <td className="px-4 py-3 text-gray-500">{invoice.issueDate}</td>
                      <td className="px-4 py-3 text-gray-500">{invoice.dueDate}</td>
                      <td className="px-4 py-3"><StatusBadge status={invoice.status} /></td>
                      <td className="px-4 py-3">
                        {invoice.matchingStatus ? (
                          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${matchingBadge[invoice.matchingStatus] ?? 'bg-gray-100 text-gray-600'}`}>
                            {t(`matching.${invoice.matchingStatus}`, invoice.matchingStatus)}
                          </span>
                        ) : <span className="text-xs text-gray-400">—</span>}
                      </td>
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
