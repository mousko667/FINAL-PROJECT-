import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Archive, Search, Download, Filter, ExternalLink, FileText, FolderPlus } from 'lucide-react'
import ArchiveFolderTree from '@/components/archive/ArchiveFolderTree'
import AssignFolderModal from '@/components/archive/AssignFolderModal'
import { formatAmount, formatDate } from '@/lib/format'

interface ArchivedInvoice {
  id: string
  referenceNumber: string
  supplierName: string
  amount: number
  currency: string
  status: string
  issueDate: string
  createdAt: string
  departmentCode?: string
  description?: string
  folderId?: string
}

export default function ArchivePage() {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [deptFilter, setDeptFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [page, setPage] = useState(0)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [assignModalInvoiceId, setAssignModalInvoiceId] = useState<string | null>(null)
  const [assignModalCurrentFolderId, setAssignModalCurrentFolderId] = useState<string | undefined>(undefined)

  // REQ-15: same download logic as InvoiceDetailPage's "Export PDF".
  const downloadPdf = async (inv: ArchivedInvoice) => {
    setDownloadingId(inv.id)
    try {
      const res = await apiClient.get(`/invoices/${inv.id}/export/pdf`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `${inv.referenceNumber}.pdf`
      a.click()
      window.URL.revokeObjectURL(url)
    } finally {
      setDownloadingId(null)
    }
  }

  const { data, isLoading } = useQuery({
    queryKey: ['archive', search, deptFilter, fromDate, toDate, page, selectedFolderId],
    queryFn: async () => {
      const params: Record<string, string | number> = {
        page,
        size: 20,
      }
      if (search) params.keyword = search
      if (deptFilter) params.department = deptFilter
      if (selectedFolderId) params.folderId = selectedFolderId
      if (fromDate) params.from = fromDate + 'T00:00:00Z'
      if (toDate) params.to = toDate + 'T23:59:59Z'
      const { data } = await apiClient.get<{ data: { content: ArchivedInvoice[]; totalElements: number; totalPages: number } }>(
        '/invoices/archive', { params }
      )
      return data.data
    },
  })

  const invoices = data?.content ?? []

  return (
    <PageRoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE', 'ROLE_ADMIN']}>
      <div className="flex h-[calc(100vh-4rem)]">
        {/* Sidebar Tree */}
        <ArchiveFolderTree 
          selectedFolderId={selectedFolderId}
          onSelectFolder={(id) => { setSelectedFolderId(id); setPage(0); }}
        />

        {/* Main Content */}
        <div className="flex-1 space-y-6 p-6 overflow-y-auto">
          <div>
            <h1 className="text-2xl font-bold text-ink">{t('archive.title', 'Archive Numérique')}</h1>
            <p className="text-sm text-ink-soft mt-0.5">
              {t('archive.subtitle', 'Toutes les factures archivées — recherche et téléchargement')}
              {data && <span className="ml-2 font-medium text-ink-soft">{data.totalElements} documents</span>}
            </p>
          </div>

          {/* Search & Filters */}
          <div className="bg-surface rounded-[4px] border border-hairline p-4 space-y-3">
          <div className="flex items-center gap-3">
            <div className="flex-1 flex items-center gap-2 border border-hairline rounded-[4px] px-3 py-2">
              <Search className="w-4 h-4 text-ink-faint shrink-0" />
              <input
                className="flex-1 text-sm outline-none bg-transparent"
                placeholder={t('archive.searchPlaceholder', 'Rechercher par référence, fournisseur, description...')}
                value={search}
                onChange={e => { setSearch(e.target.value); setPage(0) }}
              />
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-ink-faint" />
              <select value={deptFilter} onChange={e => { setDeptFilter(e.target.value); setPage(0) }}
                className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20">
                <option value="">{t('archive.allDepartments')}</option>
                {['DRH','DG','FIN','INFO','TERM','COM','QHSSE','INFRA','TECH'].map(d => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs text-ink-soft">Du</span>
              <input type="date" value={fromDate} onChange={e => { setFromDate(e.target.value); setPage(0) }}
                className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/20" />
              <span className="text-xs text-ink-soft">au</span>
              <input type="date" value={toDate} onChange={e => { setToDate(e.target.value); setPage(0) }}
                className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/20" />
            </div>
            {(search || deptFilter || fromDate || toDate) && (
              <button onClick={() => { setSearch(''); setDeptFilter(''); setFromDate(''); setToDate(''); setPage(0) }}
                className="text-xs text-crit hover:underline">
                {t('archive.resetFilters')}
              </button>
            )}
          </div>
        </div>

        {/* Results */}
        <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
          {isLoading ? (
            <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : invoices.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
              <Archive className="w-10 h-10" />
              <p className="text-sm font-medium">
                {search || deptFilter
                  ? t('archive.noResults', 'Aucun document ne correspond à vos critères de recherche.')
                  : t('archive.empty', 'Aucune facture archivée pour le moment.')}
              </p>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-ground border-b">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.supplier')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.department')}</th>
                    <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('invoice.amount')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('invoice.issueDate')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.audit.date', 'Archivé le')}</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {invoices.map(inv => (
                    <tr key={inv.id} className="hover:bg-ground">
                      <td className="px-4 py-3 num text-xs font-semibold text-ink">{inv.referenceNumber}</td>
                      <td className="px-4 py-3 text-ink-soft truncate max-w-[160px]">{inv.supplierName}</td>
                      <td className="px-4 py-3">
                        {inv.departmentCode
                          ? <span className="text-xs num bg-ground text-ink-soft px-2 py-0.5 rounded">{inv.departmentCode}</span>
                          : <span className="text-ink-faint">—</span>}
                      </td>
                      <td className="px-4 py-3 text-right font-medium text-ink">
                        {formatAmount(inv.amount)} {inv.currency}
                      </td>
                      <td className="px-4 py-3 text-ink-soft text-xs">{formatDate(inv.issueDate)}</td>
                      <td className="px-4 py-3 text-ink-soft text-xs">{formatDate(inv.createdAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2 justify-end">
                          <button
                            onClick={() => {
                              setAssignModalInvoiceId(inv.id)
                              setAssignModalCurrentFolderId(inv.folderId)
                            }}
                            className="flex items-center gap-1 text-xs text-ink-soft hover:text-primary transition-colors"
                            title={t('archiveFolders.assign')}
                          >
                            <FolderPlus className="w-3.5 h-3.5" />
                          </button>
                          <Link to={`/invoices/${inv.id}`}
                            className="flex items-center gap-1 text-xs text-primary hover:underline">
                            <ExternalLink className="w-3 h-3" /> {t('app.view')}
                          </Link>
                          <button
                            onClick={() => downloadPdf(inv)}
                            disabled={downloadingId === inv.id}
                            className="flex items-center gap-1 text-xs text-ink-soft hover:text-primary transition-colors disabled:opacity-50">
                            {downloadingId === inv.id
                              ? <Loader2 className="w-3 h-3 animate-spin" />
                              : <Download className="w-3 h-3" />} PDF
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {data && data.totalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t bg-ground">
                  <span className="text-sm text-ink-soft">{t('pagination.page')} {page + 1} / {data.totalPages} — {data.totalElements} documents</span>
                  <div className="flex gap-2">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 hover:bg-ground">{t('app.previous')}</button>
                    <button disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 hover:bg-ground">{t('app.next')}</button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

          <p className="text-xs text-ink-faint">
            <FileText className="w-3.5 h-3.5 inline mr-1" />
            {t('archive.retentionNote', 'Une empreinte SHA-256 est calculée et enregistrée au téléversement de chaque document (référence d\'intégrité). La politique de rétention OCT vise une conservation de 10 ans.')}
          </p>
        </div>
      </div>

      {assignModalInvoiceId && (
        <AssignFolderModal
          invoiceId={assignModalInvoiceId}
          currentFolderId={assignModalCurrentFolderId}
          onClose={() => setAssignModalInvoiceId(null)}
        />
      )}
    </PageRoleGuard>
  )
}
