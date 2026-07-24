import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, CheckCircle, Save } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { notifyApiError } from '@/components/ErrorToaster'

const ADMIN_ROLES = ['ROLE_ADMIN']

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

// Role labels are resolved from the shared `roles.*` i18n namespace (FR+EN),
// never hardcoded here (audit MAJEUR-F7 / §7bis.4). The N2 "none" entry uses a
// dedicated approval-matrix key since it is not a role.
const ROLE_VALUES_N1 = [
  'ROLE_DAF',
  'ROLE_VALIDATEUR_N1_DRH',
  'ROLE_VALIDATEUR_N1_DG',
  'ROLE_VALIDATEUR_N1_INFO',
  'ROLE_VALIDATEUR_N1_TERM',
  'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE',
  'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N1_TECH',
]

const ROLE_VALUES_N2 = [
  'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N2_INFRA',
  'ROLE_VALIDATEUR_N2_TECH',
]

function ApprovalMatrixPage() {
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
    onError: (e) => notifyApiError(e),
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
      <PageHeader
        title={t('admin.approvalMatrix.title')}
        subtitle={t('admin.approvalMatrix.subtitle')}
      />

      {saved && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-4 py-3">
          <CheckCircle className="w-4 h-4" />
          {t('admin.approvalMatrix.saved')}
        </div>
      )}

      <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
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
                      aria-label={t('admin.approvalMatrix.n1RoleFor', 'N1 approver role for {{dept}}', { dept: i18n.language === 'fr' ? dept.nameFr : dept.nameEn })}
                      className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 w-full max-w-[220px]"
                    >
                      {ROLE_VALUES_N1.map(v => <option key={v} value={v}>{t(`roles.${v}`, v)}</option>)}
                    </select>
                  </td>
                  <td className="px-4 py-3">
                    <input
                      type="checkbox"
                      checked={dept.requiresN2}
                      onChange={e => handleUpdate(dept, 'requiresN2', e.target.checked)}
                      aria-label={t('admin.approvalMatrix.requiresN2For', 'Requires N2 approval for {{dept}}', { dept: i18n.language === 'fr' ? dept.nameFr : dept.nameEn })}
                      className="w-4 h-4 accent-primary cursor-pointer"
                    />
                  </td>
                  <td className="px-4 py-3">
                    {dept.requiresN2 ? (
                      <select
                        value={dept.n2Role ?? ''}
                        onChange={e => handleUpdate(dept, 'n2Role', e.target.value)}
                        aria-label={t('admin.approvalMatrix.n2RoleFor', 'N2 approver role for {{dept}}', { dept: i18n.language === 'fr' ? dept.nameFr : dept.nameEn })}
                        className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 w-full max-w-[220px]"
                      >
                        <option value="">{t('admin.approvalMatrix.noneN2')}</option>
                        {ROLE_VALUES_N2.map(v => <option key={v} value={v}>{t(`roles.${v}`, v)}</option>)}
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
        {t('admin.approvalMatrix.hint')}
      </p>
    </div>
  )
}


export default function ApprovalMatrixPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <ApprovalMatrixPage />
    </PageRoleGuard>
  )
}
