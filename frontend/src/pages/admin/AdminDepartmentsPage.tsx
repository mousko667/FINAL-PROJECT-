import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, CheckCircle, XCircle, GitBranch, Plus } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'

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

function RoleLabel({ role }: { role?: string }) {
  if (!role) return <span className="text-ink-faint text-xs">—</span>
  const { t } = useTranslation()
  return (
    <span className="text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">
      {t(`roles.${role}`, role.replace('ROLE_', '').replace(/_/g, ' '))}
    </span>
  )
}

export default function AdminDepartmentsPage() {
  const { t, i18n } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<Department>>>('/departments')
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('admin.departments.title')}
        subtitle={t('admin.departments.subtitle', 'Départements OCT et chaînes d\'approbation BAP')}
        actions={
          <>
            <Link
              to="/admin/departments/new"
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium"
            >
              <Plus className="w-4 h-4" />
              {t('admin.departments.create', 'Créer un département')}
            </Link>
            <Link
              to="/admin/approval-matrix"
              className="flex items-center gap-2 border border-white/30 px-4 py-2 rounded-[4px] hover:bg-white/10 text-sm font-medium text-white"
            >
              <GitBranch className="w-4 h-4" />
              {t('admin.approvalMatrix.title', 'Matrice d\'approbation')}
            </Link>
          </>
        }
      />

      <Panel className="overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.departments.name', 'Département')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.departments.code', 'Code')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.approvalMatrix.n1Role', 'Approbateur N1')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.approvalMatrix.n2Role', 'Approbateur N2')}</th>
                <th className="text-center px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.approvalMatrix.requiresN2', '2 niveaux')}</th>
                <th className="text-center px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.users.active', 'Actif')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {(!data?.content || data.content.length === 0) ? (
                <tr>
                  <td colSpan={6} className="text-center py-16 text-ink-faint">
                    {t('app.noData')}
                  </td>
                </tr>
              ) : (
                data.content.map((dept) => (
                  <tr key={dept.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                    <td className="px-4 py-3">
                      <div className="font-medium text-ink">
                        {i18n.language === 'fr' ? dept.nameFr : dept.nameEn}
                      </div>
                      <div className="text-xs text-ink-faint">
                        {i18n.language === 'fr' ? dept.nameEn : dept.nameFr}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="num text-xs num bg-ground text-ink-soft px-2 py-0.5 rounded-[4px]">
                        {dept.code}
                      </span>
                    </td>
                    <td className="px-4 py-3"><RoleLabel role={dept.n1Role} /></td>
                    <td className="px-4 py-3">
                      {dept.requiresN2
                        ? <RoleLabel role={dept.n2Role} />
                        : <span className="text-ink-faint text-xs">—</span>}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {dept.requiresN2
                        ? <CheckCircle className="w-4 h-4 text-pos mx-auto" />
                        : <XCircle className="w-4 h-4 text-ink-faint mx-auto" />}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-block w-2 h-2 rounded-full ${dept.isActive ? 'bg-pos' : 'bg-crit'}`} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </Panel>

      <p className="text-xs text-ink-faint">
        {t('admin.departments.matrixNote', 'Pour modifier les chaînes d\'approbation, utilisez la Matrice d\'approbation.')}
      </p>
    </div>
  )
}
