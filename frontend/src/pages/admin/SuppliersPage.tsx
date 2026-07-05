import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useSuppliers, useActivateSupplier, useSuspendSupplier, useDeleteSupplier } from '@/api/suppliers'
import { useAppSelector } from '@/store/hooks'
import { SupplierStatusBadge } from '@/components/SupplierStatusBadge'
import { ExportMenu } from '@/components/ui/ExportMenu'
import { Panel } from "@/components/ui/Panel"
import {  Loader2, Search, Filter, Eye, CheckCircle, Ban, Trash2, Plus  } from 'lucide-react'
import { formatDate } from '@/lib/format'

export default function SuppliersPage() {
  const { t } = useTranslation()
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [categoryFilter, setCategoryFilter] = useState<string>('')
  const [page, setPage] = useState(0)

  const { user } = useAppSelector((state) => state.auth)
  const isAdmin = user?.roles.includes('ROLE_ADMIN')

  const { data, isLoading } = useSuppliers({
    page,
    size: 10,
    name: searchTerm || undefined,
    status: statusFilter || undefined,
    category: categoryFilter || undefined,
  })

  const SUPPLIER_CATEGORIES = ['GOODS', 'SERVICES', 'WORKS', 'CONSULTING'] as const

  const { mutate: activate } = useActivateSupplier()
  const { mutate: suspend } = useSuspendSupplier()
  const { mutate: deleteSupplier } = useDeleteSupplier()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-ink">{t('supplier.title', 'Suppliers')}</h1>
        <div className="flex items-center gap-2">
          <ExportMenu endpoint="/suppliers/export" filename="suppliers" />
          {isAdmin && (
            <Link
              to="/admin/suppliers/new"
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium"
            >
              <Plus className="w-4 h-4" />
              {t('supplier.create', 'Add Supplier')}
            </Link>
          )}
        </div>
      </div>

      <div className="flex items-center gap-4 bg-white p-4 rounded-lg border">
        <div className="relative flex-1">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
          <input
            type="text"
            placeholder={t('app.search', 'Search...')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm border rounded-[4px] focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </div>
        <div className="relative w-48">
          <Filter className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm border rounded-[4px] focus:outline-none focus:ring-2 focus:ring-primary/20 appearance-none bg-white"
          >
            <option value="">{t('app.allStatus', 'All Statuses')}</option>
            <option value="PENDING_VERIFICATION">{t('supplier.status.PENDING_VERIFICATION', 'Pending')}</option>
            <option value="ACTIVE">{t('supplier.status.ACTIVE', 'Active')}</option>
            <option value="SUSPENDED">{t('supplier.status.SUSPENDED', 'Suspended')}</option>
          </select>
        </div>
        <div className="relative w-48">
          <Filter className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
          <select
            value={categoryFilter}
            onChange={(e) => { setCategoryFilter(e.target.value); setPage(0) }}
            className="w-full pl-9 pr-4 py-2 text-sm border rounded-[4px] focus:outline-none focus:ring-2 focus:ring-primary/20 appearance-none bg-white"
          >
            <option value="">{t('supplier.category.all', 'All Categories')}</option>
            {SUPPLIER_CATEGORIES.map(c => (
              <option key={c} value={c}>{t(`supplier.category.${c}`, c)}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="bg-surface rounded-lg border border-hairline overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-ground border-b">
                <tr>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.companyName', 'Company Name')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.taxId', 'Tax ID')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.contactEmail', 'Email')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.status', 'Status')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.category', 'Category')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('supplier.fields.createdAt', 'Created Date')}</th>
                  <th className="text-right px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('app.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {(!data?.content || data.content.length === 0) ? (
                  <tr><td colSpan={7} className="text-center py-16 text-muted-foreground">{t('app.noData', 'No data found.')}</td></tr>
                ) : (
                  data.content.map((supplier) => (
                    <tr key={supplier.id} className="group hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                      <td className="px-4 py-3 font-medium">{supplier.companyName}</td>
                      <td className="px-4 py-3 text-ink-faint">{supplier.taxId}</td>
                      <td className="px-4 py-3 text-ink-faint">{supplier.contactEmail}</td>
                      <td className="px-4 py-3">
                        <SupplierStatusBadge status={supplier.status} />
                      </td>
                      <td className="px-4 py-3 text-ink-faint">
                        {supplier.category ? t(`supplier.category.${supplier.category}`, supplier.category) : '—'}
                      </td>
                      <td className="px-4 py-3 text-ink-faint">
                        {supplier.createdAt ? formatDate(supplier.createdAt) : '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                          <Link
                            to={`/admin/suppliers/${supplier.id}`}
                            className="p-1 text-ink-faint hover:text-primary rounded"
                            title={t('app.view', 'View')}
                          >
                            <Eye className="w-4 h-4" />
                          </Link>
                          {isAdmin && supplier.status !== 'ACTIVE' && (
                            <button
                              onClick={() => activate(supplier.id)}
                              className="p-1 text-ink-faint hover:text-pos rounded"
                              title={t('supplier.actions.activate', 'Activate')}
                            >
                              <CheckCircle className="w-4 h-4" />
                            </button>
                          )}
                          {isAdmin && supplier.status === 'ACTIVE' && (
                            <button
                              onClick={() => {
                                const reason = window.prompt(t('supplier.actions.suspendReason', 'Suspension reason:'))
                                if (reason) suspend({ id: supplier.id, reason })
                              }}
                              className="p-1 text-ink-faint hover:text-orange-600 rounded"
                              title={t('supplier.actions.suspend', 'Suspend')}
                            >
                              <Ban className="w-4 h-4" />
                            </button>
                          )}
                          {isAdmin && (
                            <button
                              onClick={() => {
                                if (window.confirm(t('supplier.actions.deleteConfirm', 'Are you sure?'))) {
                                  deleteSupplier(supplier.id)
                                }
                              }}
                              className="p-1 text-ink-faint hover:text-crit rounded"
                              title={t('supplier.actions.delete', 'Delete')}
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            
            {data?.totalPages && data.totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t bg-ground">
                <span className="text-sm text-ink-faint">
                  {t('app.page')} {page + 1} / {data.totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    disabled={page === 0}
                    onClick={() => setPage(p => p - 1)}
                    className="px-3 py-1 bg-white border rounded text-sm disabled:opacity-50"
                  >
                    {t('app.previous', 'Previous')}
                  </button>
                  <button
                    disabled={page >= data.totalPages - 1}
                    onClick={() => setPage(p => p + 1)}
                    className="px-3 py-1 bg-white border rounded text-sm disabled:opacity-50"
                  >
                    {t('app.next', 'Next')}
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
