import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import { invoiceService, type InvoiceFilters } from '@/services/invoiceService'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { ExportMenu } from '@/components/ui/ExportMenu'
import { Panel } from '@/components/ui/Panel'
import { ImportInvoicesModal } from '@/components/invoice/ImportInvoicesModal'
import type { InvoiceStatus } from '@/types/invoice'
import { Plus, Upload, Search, ChevronLeft, ChevronRight, Loader2, Archive, Lock } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatAmount, formatDate } from '@/lib/format'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

const ALLOWED_ROLES = [
  'ROLE_ASSISTANT_COMPTABLE',
  'ROLE_DAF',
  'ROLE_VALIDATEUR_N1_DRH',
  'ROLE_VALIDATEUR_N1_DG',
  'ROLE_VALIDATEUR_N1_INFO',
  'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N1_TERM',
  'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE',
  'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N2_INFRA',
  'ROLE_VALIDATEUR_N1_TECH',
  'ROLE_VALIDATEUR_N2_TECH'
]

const ALL_STATUSES: InvoiceStatus[] = [
  'BROUILLON', 'SOUMIS', 'EN_CONTROLE_AA', 'EN_VALIDATION_N1', 'EN_VALIDATION_N2',
  'VALIDE', 'BON_A_PAYER', 'PAYE', 'ARCHIVE', 'REJETE',
]

type TabView = 'active' | 'archive' | 'all'

const matchingBadge: Record<string, string> = {
  MATCHED:   'bg-pos-bg text-pos',
  PARTIAL:   'bg-warn-bg text-warn',
  MISMATCH:  'bg-crit-bg text-crit',
  OVERRIDDEN:'bg-hot-bg text-hot',
}

const rowHoverTint = 'hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]'

function InvoiceListPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [showImport, setShowImport] = useState(false)
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
      active ? 'border-gold-deep text-gold-deep' : 'border-transparent text-ink-faint hover:text-ink-soft'
    }`

  return (
    <div className="space-y-6 page-enter">
      {/* Page header */}
      <PageHeader
        title={t('invoice.title')}
        actions={
          <>
            <ExportMenu endpoint="/invoices/export" filename="invoices" params={{ ...effectiveFilters, from: effectiveFilters.fromDate, to: effectiveFilters.toDate }} />
            {isAA && (
              <button
                id="btn-import-invoices"
                onClick={() => setShowImport(true)}
                title={t('invoice.import.tooltip', 'Importer des factures depuis un fichier CSV ou XML')}
                className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm font-medium text-ink-soft hover:bg-ground transition-colors"
              >
                <Upload className="w-4 h-4" />
                {t('invoice.import.button', 'Importer')}
              </button>
            )}
            {isAA && (
              <button
                id="btn-new-invoice"
                onClick={() => navigate('/invoices/new')}
                title={t('invoice.newTooltip')}
                className="flex items-center gap-2 px-4 py-2 bg-oct-navy text-white rounded-[4px] text-sm font-medium hover:bg-oct-navy-light transition-colors"
              >
                <Plus className="w-4 h-4" />
                {t('invoice.new')}
              </button>
            )}
          </>
        }
      />

      {/* Department scope notice for validators */}
      {isValidator && userDeptId && (
        <div className="text-xs text-info bg-info-bg border border-info/30 rounded-[4px] px-4 py-2">
          {t('invoice.deptScopeNote', 'Showing invoices from your department only. Use the All tab to browse without restriction.')}
        </div>
      )}

      {/* Tab bar */}
      <div className="flex border-b border-hairline bg-surface rounded-t-[4px] px-4 gap-1">
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
      <div className="bg-surface border border-hairline border-t-0 rounded-b-[4px] p-4 flex flex-wrap gap-3 -mt-6 pt-4">
        <div className="flex items-center gap-2 border border-hairline rounded-[4px] px-3 py-2 text-sm focus-within:ring-2 focus-within:ring-gold-deep/30 flex-1 min-w-[200px]">
          <Search className="w-4 h-4 text-ink-faint shrink-0" />
          <input
            id="filter-reference"
            type="text"
            placeholder={t('invoice.reference')}
            className="outline-none w-full bg-transparent text-sm text-ink"
            onChange={(e) => handleFilterChange('reference', e.target.value)}
          />
        </div>
        
        <input
          id="filter-from-date"
          type="date"
          title={t('common.fromDate', 'From date')}
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm outline-none focus:border-primary"
          onChange={(e) => handleFilterChange('fromDate', e.target.value)}
        />
        <input
          id="filter-to-date"
          type="date"
          title={t('common.toDate', 'To date')}
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm outline-none focus:border-primary"
          onChange={(e) => handleFilterChange('toDate', e.target.value)}
        />

        {/* Status filter — only shown in 'all' tab */}
        {tab === 'all' && (
          <select
            id="filter-status"
            aria-label={t('invoice.filterStatus')}
            className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30 min-w-[160px]"
            onChange={(e) => handleFilterChange('status', e.target.value)}
          >
            <option value="">{t('app.all')}</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>{t(`status.${s}`)}</option>
            ))}
          </select>
        )}


      </div>

      {/* Table */}
      <Panel className="overflow-x-auto">
        {isLoading && (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        )}

        {isError && (
          <div className="text-center py-20 text-crit text-sm">{t('app.error')}</div>
        )}

        {!isLoading && !isError && (
          <>
            <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-ground">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.reference')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.supplier')}</th>
                  <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.amount')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.issueDate')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.dueDate')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.status')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('matching.columnHeader', 'Matching')}</th>
                  <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.department')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center py-16 text-ink-faint">{t('app.noData')}</td>
                  </tr>
                ) : (
                  invoices.map((invoice) => (
                    <tr
                      key={invoice.id}
                      id={`invoice-row-${invoice.id}`}
                      className={`cursor-pointer transition-colors ${rowHoverTint}`}
                      onClick={() => navigate(`/invoices/${invoice.id}`)}
                    >
                      <td className="px-4 py-3 font-medium text-gold-deep">
                        <span className="num inline-flex items-center gap-1.5">
                          {invoice.dataSensitivity === 'CONFIDENTIAL' && (
                            <Lock className="w-3.5 h-3.5 text-crit" aria-label={t('sensitivity.CONFIDENTIAL', 'Confidential')} />
                          )}
                          {invoice.referenceNumber}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-ink-soft">{invoice.supplierName}</td>
                      <td className="px-4 py-3 text-right">
                        <span className="num text-ink">{formatAmount(invoice.amount)}</span>{' '}
                        <span className="text-ink-faint text-xs">{invoice.currency}</span>
                      </td>
                      <td className="px-4 py-3 text-ink-soft">{formatDate(invoice.issueDate)}</td>
                      <td className="px-4 py-3 text-ink-soft">{formatDate(invoice.dueDate)}</td>
                      <td className="px-4 py-3"><StatusBadge status={invoice.status} /></td>
                      <td className="px-4 py-3">
                        {invoice.matchingStatus ? (
                          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${matchingBadge[invoice.matchingStatus] ?? 'bg-ground text-ink-soft'}`}>
                            {t(`matching.${invoice.matchingStatus}`, invoice.matchingStatus)}
                          </span>
                        ) : <span className="text-xs text-ink-faint">—</span>}
                      </td>
                      <td className="px-4 py-3 text-ink-soft">{(i18n.language === 'en' ? invoice.departmentNameEn : invoice.departmentNameFr) ?? invoice.departmentCode ?? '—'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-hairline bg-ground">
                <span className="text-sm text-ink-faint">
                  {t('pagination.page')} {currentPage + 1} {t('pagination.of')} {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    id="btn-prev-page"
                    disabled={currentPage === 0}
                    onClick={() => setFilters((p) => ({ ...p, page: currentPage - 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface transition-colors text-ink-soft"
                  >
                    <ChevronLeft className="w-4 h-4" />
                    {t('pagination.previous')}
                  </button>
                  <button
                    id="btn-next-page"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setFilters((p) => ({ ...p, page: currentPage + 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface transition-colors text-ink-soft"
                  >
                    {t('pagination.next')}
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </Panel>

      {showImport && (
        <ImportInvoicesModal
          onClose={() => setShowImport(false)}
          onImported={() => queryClient.invalidateQueries({ queryKey: ['invoices'] })}
        />
      )}
    </div>
  )
}


export default function InvoiceListPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ALLOWED_ROLES}>
      <InvoiceListPage />
    </PageRoleGuard>
  )
}
