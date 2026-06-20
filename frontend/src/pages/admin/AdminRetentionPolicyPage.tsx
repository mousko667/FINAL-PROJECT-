import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Save, Archive } from 'lucide-react'

interface RetentionPolicy {
  retentionYears: number
  active: boolean
  lastSweepAt?: string | null
  lastFlaggedCount?: number | null
  updatedAt?: string | null
}

export default function AdminRetentionPolicyPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [retentionYears, setRetentionYears] = useState(10)
  const [active, setActive] = useState(true)

  const { data: policy, isLoading } = useQuery({
    queryKey: ['retention-policy'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: RetentionPolicy }>('/retention-policy')
      return data.data
    },
  })

  useEffect(() => {
    if (policy) {
      setRetentionYears(policy.retentionYears)
      setActive(policy.active)
    }
  }, [policy])

  const saveMutation = useMutation({
    mutationFn: () => apiClient.put('/retention-policy', { retentionYears, active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['retention-policy'] }),
  })

  const formatDate = (iso?: string | null) =>
    iso ? new Date(iso).toLocaleString(i18n.language) : t('retentionPolicy.never', 'Jamais')

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-2xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('retentionPolicy.title', 'Politique de rétention')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('retentionPolicy.subtitle', 'Durée de conservation des documents de facture avant signalement pour disposition.')}
          </p>
        </div>

        <p className="text-xs text-gray-500 bg-gray-50 border rounded-lg p-3">
          {t('retentionPolicy.note', 'Le balayage est non destructif : les documents dépassant la durée de rétention sont signalés (audit) pour qu’un administrateur décide de leur disposition. Aucune suppression automatique.')}
        </p>

        {isLoading ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <div className="bg-white rounded-xl border p-6 space-y-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('retentionPolicy.years', 'Durée de rétention (années)')} *
              </label>
              <input type="number" min={1} max={100} value={retentionYears}
                onChange={e => setRetentionYears(Number(e.target.value))}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              <p className="text-xs text-gray-400 mt-1">
                {t('retentionPolicy.yearsHint', 'Les documents plus anciens que cette durée seront signalés au prochain balayage quotidien.')}
              </p>
            </div>

            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} />
              {t('retentionPolicy.active', 'Balayage actif')}
            </label>

            <div className="grid grid-cols-2 gap-4 text-sm bg-gray-50 border rounded-lg p-4">
              <div>
                <span className="block text-xs text-gray-500">{t('retentionPolicy.lastSweep', 'Dernier balayage')}</span>
                <span className="font-medium text-gray-800">{formatDate(policy?.lastSweepAt)}</span>
              </div>
              <div>
                <span className="block text-xs text-gray-500">{t('retentionPolicy.lastFlagged', 'Documents signalés')}</span>
                <span className="font-medium text-gray-800">{policy?.lastFlaggedCount ?? '—'}</span>
              </div>
            </div>

            {saveMutation.isError && (
              <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
                {t('retentionPolicy.saveError', 'Échec de l’enregistrement de la politique.')}
              </p>
            )}
            {saveMutation.isSuccess && (
              <p className="text-sm text-green-700 bg-green-50 p-3 rounded-md border border-green-200">
                {t('retentionPolicy.saved', 'Politique enregistrée.')}
              </p>
            )}

            <div className="flex items-center justify-end pt-2 border-t">
              <button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
                {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                {t('app.save', 'Enregistrer')}
              </button>
            </div>
          </div>
        )}

        <p className="flex items-center gap-1.5 text-xs text-gray-400">
          <Archive className="w-3.5 h-3.5" />
          {t('retentionPolicy.scopeNote', 'S’applique aux documents de facture archivés.')}
        </p>
      </div>
    </PageRoleGuard>
  )
}
