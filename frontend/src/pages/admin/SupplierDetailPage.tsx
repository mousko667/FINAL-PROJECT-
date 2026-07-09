import { useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  useSupplier,
  useSupplierDocuments,
  useSupplierPerformance,
  useActivateSupplier,
  useSuspendSupplier,
  useDeleteSupplier,
  useUploadSupplierDocument
} from '@/api/suppliers'
import { useAppSelector } from '@/store/hooks'
import { SupplierStatusBadge } from '@/components/SupplierStatusBadge'
import { SupplierRelationship } from '@/components/supplier/SupplierRelationship'
import { formatDate } from '@/lib/format'
import { Loader2, ArrowLeft, CheckCircle, Ban, Trash2, Building, Mail, Phone, MapPin, Calendar, FileText, Activity, Upload } from 'lucide-react'

export default function SupplierDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<'DETAILS' | 'DOCUMENTS' | 'PERFORMANCE' | 'RELATIONSHIP'>('DETAILS')
  const [docType, setDocType] = useState<'TAX_CERTIFICATE' | 'CONTRACT' | 'OTHER'>('TAX_CERTIFICATE')
  const fileInputRef = useRef<HTMLInputElement>(null)
  
  const { user } = useAppSelector((state) => state.auth)
  // Both Admin and Accounting Assistant manage supplier lifecycle (briefing §5.2)
  const isAdmin = user?.roles.includes('ROLE_ADMIN')
  const canManageSupplier = user?.roles.some(r => r === 'ROLE_ADMIN' || r === 'ROLE_ASSISTANT_COMPTABLE')

  const { data: supplier, isLoading: loadingInfo } = useSupplier(id)
  const { data: documents, isLoading: loadingDocs } = useSupplierDocuments(id)
  const { data: perf, isLoading: loadingPerf } = useSupplierPerformance(id)

  const { mutate: activate, isPending: activating } = useActivateSupplier()
  const { mutateAsync: uploadDocument, isPending: uploading } = useUploadSupplierDocument()
  const { mutate: suspend, isPending: suspending } = useSuspendSupplier()
  const { mutate: deleteSupplier, isPending: deleting } = useDeleteSupplier()

  if (loadingInfo) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!supplier) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold text-ink">{t('app.notFound', 'Not Found')}</h2>
        <button onClick={() => navigate(-1)} className="text-primary hover:underline mt-4">
          {t('app.back', 'Go back')}
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/admin/suppliers')} className="p-2 hover:bg-ground rounded-full transition-colors">
          <ArrowLeft className="w-5 h-5 text-ink-soft" />
        </button>
        <h1 className="text-2xl font-bold text-ink flex-1">{supplier.companyName}</h1>
        <SupplierStatusBadge status={supplier.status} className="text-sm px-3 py-1" />
      </div>

      {canManageSupplier && (
        <div className="flex items-center gap-3 bg-surface p-4 rounded-[4px] border border-hairline">
          {supplier.status !== 'ACTIVE' && (
            <button
              onClick={() => activate(supplier.id)}
              disabled={activating}
              className="flex items-center gap-2 px-4 py-2 bg-pos-bg text-pos hover:bg-pos-bg rounded-[4px] text-sm font-medium transition-colors disabled:opacity-50"
            >
              <CheckCircle className="w-4 h-4" />
              {t('supplier.actions.activate', 'Activate')}
            </button>
          )}
          {supplier.status === 'ACTIVE' && (
            <button
              onClick={() => {
                const reason = window.prompt(t('supplier.actions.suspendReason', 'Suspension reason:'))
                if (reason) suspend({ id: supplier.id, reason })
              }}
              disabled={suspending}
              className="flex items-center gap-2 px-4 py-2 bg-hot-bg text-hot hover:bg-hot-bg rounded-[4px] text-sm font-medium transition-colors disabled:opacity-50"
            >
              <Ban className="w-4 h-4" />
              {t('supplier.actions.suspend', 'Suspend')}
            </button>
          )}
          <div className="flex-1" />
          {/* Delete is Admin-only */}
          {isAdmin && (
            <button
              onClick={() => {
                if (window.confirm(t('supplier.actions.deleteConfirm', 'Are you sure you want to delete this supplier?'))) {
                  deleteSupplier(supplier.id)
                  navigate('/admin/suppliers')
                }
              }}
              disabled={deleting}
              className="flex items-center gap-2 px-4 py-2 bg-crit-bg text-crit hover:bg-crit-bg rounded-[4px] text-sm font-medium transition-colors disabled:opacity-50"
            >
              <Trash2 className="w-4 h-4" />
              {t('supplier.actions.delete', 'Delete')}
            </button>
          )}
        </div>
      )}

      {/* Tabs */}
      <div className="flex items-center gap-6 border-b border-hairline">
        <button
          onClick={() => setActiveTab('DETAILS')}
          className={`pb-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'DETAILS' ? 'border-primary text-primary' : 'border-transparent text-ink-soft hover:text-ink-soft'
          }`}
        >
          {t('supplier.tabs.details', 'Details')}
        </button>
        <button
          onClick={() => setActiveTab('DOCUMENTS')}
          className={`pb-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'DOCUMENTS' ? 'border-primary text-primary' : 'border-transparent text-ink-soft hover:text-ink-soft'
          }`}
        >
          {t('supplier.tabs.documents', 'Documents')}
        </button>
        <button
          onClick={() => setActiveTab('PERFORMANCE')}
          className={`pb-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'PERFORMANCE' ? 'border-primary text-primary' : 'border-transparent text-ink-soft hover:text-ink-soft'
          }`}
        >
          {t('supplier.tabs.performance', 'Performance')}
        </button>
        <button
          onClick={() => setActiveTab('RELATIONSHIP')}
          className={`pb-4 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'RELATIONSHIP' ? 'border-primary text-primary' : 'border-transparent text-ink-soft hover:text-ink-soft'
          }`}
        >
          {t('supplier.tabs.relationship', 'Contrats & communications')}
        </button>
      </div>

      {activeTab === 'RELATIONSHIP' && id && (
        <SupplierRelationship supplierId={id} canEdit={!!canManageSupplier} />
      )}

      {activeTab === 'DETAILS' && (
        <div className="bg-surface rounded-[4px] border border-hairline p-6 grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="space-y-6">
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <Building className="w-4 h-4" />
                {t('supplier.fields.companyName', 'Company Name')}
              </label>
              <p className="font-medium text-ink">{supplier.companyName}</p>
            </div>
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <FileText className="w-4 h-4" />
                {t('supplier.fields.taxId', 'Tax ID')}
              </label>
              <p className="font-medium text-ink">{supplier.taxId}</p>
            </div>
          </div>
          <div className="space-y-6">
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <Mail className="w-4 h-4" />
                {t('supplier.fields.contactEmail', 'Email')}
              </label>
              <p className="font-medium text-ink">{supplier.contactEmail}</p>
            </div>
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <Phone className="w-4 h-4" />
                {t('supplier.fields.contactPhone', 'Phone')}
              </label>
              <p className="font-medium text-ink">{supplier.contactPhone || '—'}</p>
            </div>
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <MapPin className="w-4 h-4" />
                {t('supplier.fields.address', 'Address')}
              </label>
              <p className="font-medium text-ink">{supplier.address || '—'}</p>
            </div>
            <div>
              <label className="flex items-center gap-2 text-sm text-ink-soft mb-1">
                <Calendar className="w-4 h-4" />
                {t('supplier.fields.onboardingDate', 'Created Date')}
              </label>
              <p className="font-medium text-ink">
                {supplier.createdAt ? formatDate(supplier.createdAt) : '—'}
              </p>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'DOCUMENTS' && (
        <div className="space-y-4">
          <div className="bg-surface rounded-[4px] border border-hairline p-5 flex flex-col sm:flex-row items-start sm:items-end gap-4">
            <div className="flex-1 w-full">
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('supplier.document.type.label', 'Document Type')}
              </label>
              <select
                value={docType}
                onChange={(e) => setDocType(e.target.value as typeof docType)}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="TAX_CERTIFICATE">{t('supplier.document.type.TAX_CERTIFICATE', 'Tax Certificate')}</option>
                <option value="CONTRACT">{t('supplier.document.type.CONTRACT', 'Contract')}</option>
                <option value="OTHER">{t('supplier.document.type.OTHER', 'Other')}</option>
              </select>
            </div>
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.xlsx"
                className="hidden"
                onChange={async (e) => {
                  const file = e.target.files?.[0]
                  if (!file || !id) return
                  const formData = new FormData()
                  formData.append('file', file)
                  formData.append('documentType', docType)
                  await uploadDocument({ id, formData })
                  if (fileInputRef.current) fileInputRef.current.value = ''
                }}
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors"
              >
                {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {t('supplier.document.upload', 'Upload Document')}
              </button>
            </div>
          </div>

          <div className="bg-surface rounded-[4px] border border-hairline">
            {loadingDocs ? (
              <div className="flex justify-center py-10"><Loader2 className="w-6 h-6 animate-spin" /></div>
            ) : !documents?.length ? (
              <div className="text-center py-16 text-muted-foreground">{t('app.noData', 'No documents found.')}</div>
            ) : (
              <ul className="divide-y">
                {documents.map((doc) => (
                  <li key={doc.id} className="p-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <FileText className="w-5 h-5 text-ink-faint" />
                      <div>
                        <p className="font-medium text-ink">{doc.originalFilename}</p>
                        <p className="text-sm text-ink-soft">{t(`supplier.document.type.${doc.documentType}`, doc.documentType)}</p>
                      </div>
                    </div>
                    <div className="text-sm text-ink-faint">
                      {formatDate(doc.uploadedAt)}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {activeTab === 'PERFORMANCE' && (
        <div className="bg-surface rounded-[4px] border border-hairline p-6">
          {loadingPerf ? (
            <div className="flex justify-center py-10"><Loader2 className="w-6 h-6 animate-spin" /></div>
          ) : !perf ? (
            <div className="text-center py-16 text-muted-foreground">{t('app.noData', 'No performance data limit.')}</div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="p-6 bg-info-bg/50 rounded-[4px] border border-info/30 flex flex-col items-center justify-center text-center">
                <Activity className="w-8 h-8 text-info mb-3" />
                <h3 className="text-sm font-medium text-ink-soft">{t('supplier.performance.accuracyRate', 'Accuracy Rate')}</h3>
                <p className="text-2xl font-bold text-ink mt-1">{(perf.accuracyRate * 100).toFixed(1)}%</p>
              </div>
              <div className="p-6 bg-crit-bg/50 rounded-[4px] border border-crit/30 flex flex-col items-center justify-center text-center">
                <Ban className="w-8 h-8 text-crit mb-3" />
                <h3 className="text-sm font-medium text-ink-soft">{t('supplier.performance.rejectionRate', 'Rejection Rate')}</h3>
                <p className="text-2xl font-bold text-ink mt-1">{(perf.rejectionRate * 100).toFixed(1)}%</p>
              </div>
              <div className="p-6 bg-pos-bg/50 rounded-[4px] border border-pos/30 flex flex-col items-center justify-center text-center">
                <Calendar className="w-8 h-8 text-pos mb-3" />
                <h3 className="text-sm font-medium text-ink-soft">{t('supplier.performance.averagePaymentTime', 'Avg. Payment Time')}</h3>
                <p className="text-2xl font-bold text-ink mt-1">{perf.averagePaymentTimeDays.toFixed(1)} {t('app.days', 'days')}</p>
              </div>
            </div>
          )}
        </div>
      )}

    </div>
  )
}
