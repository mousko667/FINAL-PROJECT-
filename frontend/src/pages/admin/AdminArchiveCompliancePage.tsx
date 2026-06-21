import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, ShieldCheck } from 'lucide-react'

interface CoverageSection {
  archivedInvoices: number
  archivedWithDocument: number
  archivedWithoutDocument: number
  coverageRate: number
}
interface IntegritySection {
  totalDocuments: number
  withChecksum: number
  missingChecksum: number
  integrityRate: number
}
interface RetentionSection {
  status: string
  retentionYears: number
  active: boolean
  sweepOverdue: boolean
}
interface LifecycleSection {
  pending: number
  retained: number
  purged: number
  versionedDocuments: number
}
interface ArchiveComplianceReport {
  generatedAt: string
  coverage: CoverageSection
  integrity: IntegritySection
  retention: RetentionSection
  lifecycle: LifecycleSection
}

const pct = (rate: number) => `${Math.round(rate * 1000) / 10}%`

function Row({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex items-center justify-between text-sm py-1">
      <span className="text-gray-500">{label}</span>
      <span className="font-medium text-gray-800">{value}</span>
    </div>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl border p-5 space-y-1">
      <h2 className="text-sm font-semibold text-gray-900 mb-2">{title}</h2>
      {children}
    </div>
  )
}

export default function AdminArchiveCompliancePage() {
  const { t, i18n } = useTranslation()

  const { data: report, isLoading } = useQuery({
    queryKey: ['archive-compliance'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ArchiveComplianceReport }>('/compliance/archive-report')
      return data.data
    },
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('archiveCompliance.title', 'Conformité des archives')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('archiveCompliance.subtitle', "État de conformité du dépôt d'archives documentaires. Aucune donnée financière.")}
          </p>
        </div>

        {isLoading || !report ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <>
            <p className="flex items-center gap-1.5 text-xs text-gray-400">
              <ShieldCheck className="w-3.5 h-3.5" />
              {t('archiveCompliance.generatedAt', 'Généré le')} {new Date(report.generatedAt).toLocaleString(i18n.language)}
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Card title={t('archiveCompliance.coverage', "Couverture d'archivage")}>
                <Row label={t('archiveCompliance.coverageRate', 'Taux de couverture')} value={pct(report.coverage.coverageRate)} />
                <Row label={t('archiveCompliance.archivedInvoices', 'Factures archivées')} value={report.coverage.archivedInvoices} />
                <Row label={t('archiveCompliance.archivedWithDocument', 'Avec document')} value={report.coverage.archivedWithDocument} />
                <Row label={t('archiveCompliance.archivedWithoutDocument', 'Sans document')} value={report.coverage.archivedWithoutDocument} />
              </Card>

              <Card title={t('archiveCompliance.integrity', 'Intégrité (SHA-256)')}>
                <Row label={t('archiveCompliance.integrityRate', "Taux d'intégrité")} value={pct(report.integrity.integrityRate)} />
                <Row label={t('archiveCompliance.totalDocuments', 'Documents totaux')} value={report.integrity.totalDocuments} />
                <Row label={t('archiveCompliance.withChecksum', 'Avec empreinte')} value={report.integrity.withChecksum} />
                <Row label={t('archiveCompliance.missingChecksum', 'Sans empreinte')} value={report.integrity.missingChecksum} />
              </Card>

              <Card title={t('archiveCompliance.retention', 'Rétention')}>
                <Row label={t('archiveCompliance.retention', 'Rétention')} value={report.retention.status} />
                <Row label={t('retentionPolicy.years', 'Durée de rétention (années)')} value={report.retention.retentionYears} />
              </Card>

              <Card title={t('archiveCompliance.lifecycle', 'Cycle de vie')}>
                <Row label={t('archiveCompliance.pending', 'En attente')} value={report.lifecycle.pending} />
                <Row label={t('archiveCompliance.retained', 'Conservés')} value={report.lifecycle.retained} />
                <Row label={t('archiveCompliance.purged', 'Purgés')} value={report.lifecycle.purged} />
                <Row label={t('archiveCompliance.versionedDocuments', 'Documents versionnés')} value={report.lifecycle.versionedDocuments} />
              </Card>
            </div>
          </>
        )}
      </div>
    </PageRoleGuard>
  )
}
