import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, SlidersHorizontal, Save, CheckCircle, AlertCircle } from 'lucide-react'
import { formatDateTime } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'

interface MatchingConfig {
  id: string
  tolerancePercentage: number
  toleranceAmount: number
  requireGrn: boolean
  isActive: boolean
  updatedAt: string
}

export default function AdminMatchingConfigPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [tolerancePercentage, setTolerancePercentage] = useState('')
  const [toleranceAmount, setToleranceAmount] = useState('')
  const [requireGrn, setRequireGrn] = useState(false)
  const [saved, setSaved] = useState(false)
  const [formError, setFormError] = useState('')

  const { data: config, isLoading } = useQuery({
    queryKey: ['matching-config'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: MatchingConfig }>('/matching-config')
      return data.data
    },
  })

  useEffect(() => {
    if (config) {
      setTolerancePercentage(String(config.tolerancePercentage))
      setToleranceAmount(String(config.toleranceAmount))
      setRequireGrn(config.requireGrn)
    }
  }, [config])

  const saveMutation = useMutation({
    mutationFn: () =>
      apiClient.post('/matching-config', {
        tolerancePercentage: Number(tolerancePercentage),
        toleranceAmount: Number(toleranceAmount),
        requireGrn,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['matching-config'] })
      setSaved(true)
      setFormError('')
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      setFormError(err?.response?.data?.message ?? t('admin.matchingConfig.saveError', 'Could not save the configuration.'))
    },
  })

  const submit = () => {
    setFormError('')
    const pct = Number(tolerancePercentage)
    const amt = Number(toleranceAmount)
    if (Number.isNaN(pct) || pct < 0 || pct > 100) {
      setFormError(t('admin.matchingConfig.badPercentage', 'Tolerance percentage must be between 0 and 100.'))
      return
    }
    if (Number.isNaN(amt) || amt < 0) {
      setFormError(t('admin.matchingConfig.badAmount', 'Tolerance amount must be 0 or greater.'))
      return
    }
    saveMutation.mutate()
  }

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="space-y-6 page-enter max-w-2xl">
        <PageHeader
          title={<span className="flex items-center gap-2"><SlidersHorizontal className="w-6 h-6" aria-hidden />{t('admin.matchingConfig.title', 'Three-Way Matching Configuration')}</span>}
          subtitle={t('admin.matchingConfig.subtitle', 'Tolerance thresholds applied when matching invoices against purchase orders and goods receipts.')}
        />

        {isLoading ? (
          <div className="p-8 flex justify-center"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
        ) : (
          <Panel className="p-5 space-y-5">
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('admin.matchingConfig.tolerancePercentage', 'Tolerance (%)')}
              </label>
              <input
                type="number" min="0" max="100" step="0.01"
                value={tolerancePercentage}
                onChange={(e) => setTolerancePercentage(e.target.value)}
                className="w-full sm:w-48 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <p className="text-xs text-ink-faint mt-1">
                {t('admin.matchingConfig.tolerancePercentageHint', 'Maximum percentage difference allowed between invoice and PO amounts.')}
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('admin.matchingConfig.toleranceAmount', 'Tolerance (amount)')}
              </label>
              <input
                type="number" min="0" step="0.01"
                value={toleranceAmount}
                onChange={(e) => setToleranceAmount(e.target.value)}
                className="w-full sm:w-48 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <p className="text-xs text-ink-faint mt-1">
                {t('admin.matchingConfig.toleranceAmountHint', 'Maximum absolute amount difference allowed, regardless of percentage.')}
              </p>
            </div>

            <label className="flex items-center gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={requireGrn}
                onChange={(e) => setRequireGrn(e.target.checked)}
                className="w-4 h-4 rounded border-hairline text-primary focus:ring-primary/30"
              />
              <span className="text-sm font-medium text-ink-soft">
                {t('admin.matchingConfig.requireGrn', 'Require a Goods Receipt Note (GRN) for matching')}
              </span>
            </label>

            {formError && (
              <p className="flex items-center gap-1.5 text-sm text-crit">
                <AlertCircle className="w-4 h-4 shrink-0" /> {formError}
              </p>
            )}

            <div className="flex items-center gap-3 pt-1">
              <button
                onClick={submit}
                disabled={saveMutation.isPending}
                className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded-[4px] px-4 py-2 text-sm font-medium disabled:opacity-50"
              >
                {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                {t('admin.matchingConfig.save', 'Save configuration')}
              </button>
              {saved && (
                <span className="inline-flex items-center gap-1 text-sm text-pos">
                  <CheckCircle className="w-4 h-4" /> {t('admin.matchingConfig.saved', 'Saved')}
                </span>
              )}
            </div>

            {config?.updatedAt && (
              <p className="text-xs text-ink-faint border-t border-hairline pt-3">
                {t('admin.matchingConfig.lastUpdated', 'Last updated')}: {formatDateTime(config.updatedAt)}
              </p>
            )}
          </Panel>
        )}
      </div>
    </PageRoleGuard>
  )
}
