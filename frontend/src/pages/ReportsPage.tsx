import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { reportService } from '@/services/reportService'
import { RoleGuard } from '@/components/auth/RoleGuard'
import { FileSpreadsheet, FileBadge, FileCheck, Loader2, Download } from 'lucide-react'

export default function ReportsPage() {
  const { t } = useTranslation()
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')

  const excelMutation = useMutation({
    mutationFn: () => reportService.exportExcel({ fromDate: fromDate || undefined, toDate: toDate || undefined }),
  })

  const complianceMutation = useMutation({
    mutationFn: () => reportService.exportCompliancePdf(fromDate, toDate),
  })

  return (
    <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']}>
      <div className="max-w-3xl mx-auto space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('reports.title')}</h1>

        {/* Date range selector */}
        <div className="bg-white rounded-xl border p-5 flex flex-wrap gap-4 items-end">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('reports.startDate')}</label>
            <input
              id="report-start-date"
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('reports.endDate')}</label>
            <input
              id="report-end-date"
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
        </div>

        {/* Export cards */}
        <div className="grid grid-cols-1 gap-4">
          {/* Excel */}
          <div className="bg-white rounded-xl border p-6 flex items-start justify-between">
            <div className="flex items-start gap-4">
              <div className="p-3 bg-green-50 rounded-xl">
                <FileSpreadsheet className="w-6 h-6 text-green-600" />
              </div>
              <div>
                <h3 className="font-semibold text-gray-800">{t('reports.exportExcel')}</h3>
                <p className="text-sm text-muted-foreground mt-0.5">Exporte toutes les factures filtrées au format XLSX</p>
              </div>
            </div>
            <button
              id="btn-export-excel"
              disabled={excelMutation.isPending}
              onClick={() => excelMutation.mutate()}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50 transition-colors"
            >
              {excelMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
              {t('app.export')}
            </button>
          </div>

          {/* Compliance PDF */}
          <div className="bg-white rounded-xl border p-6 flex items-start justify-between">
            <div className="flex items-start gap-4">
              <div className="p-3 bg-purple-50 rounded-xl">
                <FileCheck className="w-6 h-6 text-purple-600" />
              </div>
              <div>
                <h3 className="font-semibold text-gray-800">{t('reports.exportPdfCompliance')}</h3>
                <p className="text-sm text-muted-foreground mt-0.5">Rapport de conformité pour la période sélectionnée</p>
              </div>
            </div>
            <button
              id="btn-export-compliance-pdf"
              disabled={complianceMutation.isPending || !fromDate || !toDate}
              onClick={() => complianceMutation.mutate()}
              className="flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg text-sm font-medium hover:bg-purple-700 disabled:opacity-50 transition-colors"
            >
              {complianceMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
              {t('app.export')}
            </button>
          </div>

          {/* Audit PDF note */}
          <div className="bg-white rounded-xl border p-6 flex items-start gap-4">
            <div className="p-3 bg-blue-50 rounded-xl">
              <FileBadge className="w-6 h-6 text-blue-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-800">{t('reports.exportPdfAudit')}</h3>
              <p className="text-sm text-muted-foreground mt-0.5">
                Disponible depuis la page de détail d'une facture
              </p>
            </div>
          </div>
        </div>
      </div>
    </RoleGuard>
  )
}
