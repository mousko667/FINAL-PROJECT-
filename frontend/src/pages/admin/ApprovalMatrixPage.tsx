import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, CheckCircle, Save } from 'lucide-react'

interface Department {
  id: string
  code: string
  nameEn: string
  nameFr: string
  n1Role: string
  n2Role?: string
  requiresN2: boolean
  isActive: boolean
}

const ROLE_OPTIONS_N1 = [
  { value: 'ROLE_DAF',                  label: 'CFO / DAF' },
  { value: 'ROLE_VALIDATEUR_N1_DRH',    label: 'HR Director (DRH)' },
  { value: 'ROLE_VALIDATEUR_N1_DG',     label: 'General Manager (DG)' },
  { value: 'ROLE_VALIDATEUR_N1_INFO',   label: 'IT Manager (RSI)' },
  { value: 'ROLE_VALIDATEUR_N1_TERM',   label: 'Terminal Manager (DEX)' },
  { value: 'ROLE_VALIDATEUR_N1_COM',    label: 'Communication Manager' },
  { value: 'ROLE_VALIDATEUR_N1_QHSSE',  label: 'QHSSE Manager' },
  { value: 'ROLE_VALIDATEUR_N1_INFRA',  label: 'Infrastructure Manager' },
  { value: 'ROLE_VALIDATEUR_N1_TECH',   label: 'Workshop Manager' },
]

const ROLE_OPTIONS_N2 = [
  { value: '', label: '— None (single-level) —' },
  { value: 'ROLE_VALIDATEUR_N2_INFO',   label: 'CIO (DSI)' },
  { value: 'ROLE_VALIDATEUR_N2_INFRA',  label: 'Infrastructure Director' },
  { value: 'ROLE_VALIDATEUR_N2_TECH',   label: 'Technical Director' },
]

export default function ApprovalMatrixPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)

  const { data: departments, isLoading } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Department[] } }>('/departments')
      return data.data?.content ?? []
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Partial<Department> }) =>
      apiClient.put(`/departments/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
  })

  const handleUpdate = (dept: Department, field: 'n1Role' | 'n2Role' | 'requiresN2', value: string | boolean) => {
    updateMutation.mutate({ id: dept.id, body: { ...dept, [field]: value } })
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">{t('admin.approvalMatrix.title')}</h1>
        <p className="text-sm text-ink-soft mt-1">{t('admin.approvalMatrix.subtitle')}</p>
      </div>

      {saved && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-4 py-3">
          <CheckCircle className="w-4 h-4" />
          {t('admin.approvalMatrix.saved')}
        </div>
      )}

      <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.approvalMatrix.dept')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.approvalMatrix.n1Role')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.approvalMatrix.requiresN2')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.approvalMatrix.n2Role')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(departments ?? []).map(dept => (
                <tr key={dept.id} className="hover:bg-ground">
                  <td className="px-4 py-3">
                    <div className="font-medium text-ink">
                      {i18n.language === 'fr' ? dept.nameFr : dept.nameEn}
                    </div>
                    <div className="text-xs text-ink-faint num">{dept.code}</div>
                  </td>
                  <td className="px-4 py-3">
                    <select
                      value={dept.n1Role}
                      onChange={e => handleUpdate(dept, 'n1Role', e.target.value)}
                      className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 w-full max-w-[220px]"
                    >
                      {ROLE_OPTIONS_N1.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                  </td>
                  <td className="px-4 py-3">
                    <input
                      type="checkbox"
                      checked={dept.requiresN2}
                      onChange={e => handleUpdate(dept, 'requiresN2', e.target.checked)}
                      className="w-4 h-4 accent-primary cursor-pointer"
                    />
                  </td>
                  <td className="px-4 py-3">
                    {dept.requiresN2 ? (
                      <select
                        value={dept.n2Role ?? ''}
                        onChange={e => handleUpdate(dept, 'n2Role', e.target.value)}
                        className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 w-full max-w-[220px]"
                      >
                        {ROLE_OPTIONS_N2.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                      </select>
                    ) : (
                      <span className="text-ink-faint text-xs">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <p className="text-xs text-ink-faint">
        Changes take effect immediately. The routing engine reads this matrix on every invoice submission.
      </p>
    </div>
  )
}
