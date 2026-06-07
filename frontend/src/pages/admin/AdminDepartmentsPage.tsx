import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, CheckCircle, XCircle, GitBranch } from 'lucide-react'

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
  if (!role) return <span className="text-gray-400 text-xs">—</span>
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('admin.departments.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('admin.departments.subtitle', 'Départements OCT et chaînes d\'approbation BAP')}
          </p>
        </div>
        <Link
          to="/admin/approval-matrix"
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-lg hover:bg-primary/90 text-sm font-medium"
        >
          <GitBranch className="w-4 h-4" />
          {t('admin.approvalMatrix.title', 'Matrice d\'approbation')}
        </Link>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.departments.name', 'Département')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.departments.code', 'Code')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.approvalMatrix.n1Role', 'Approbateur N1')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.approvalMatrix.n2Role', 'Approbateur N2')}</th>
                <th className="text-center px-4 py-3 font-medium text-gray-600">{t('admin.approvalMatrix.requiresN2', '2 niveaux')}</th>
                <th className="text-center px-4 py-3 font-medium text-gray-600">{t('admin.users.active', 'Actif')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(!data?.content || data.content.length === 0) ? (
                <tr>
                  <td colSpan={6} className="text-center py-16 text-muted-foreground">
                    {t('app.noData')}
                  </td>
                </tr>
              ) : (
                data.content.map((dept) => (
                  <tr key={dept.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">
                        {i18n.language === 'fr' ? dept.nameFr : dept.nameEn}
                      </div>
                      <div className="text-xs text-gray-400">
                        {i18n.language === 'fr' ? dept.nameEn : dept.nameFr}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-xs font-mono bg-gray-100 text-gray-700 px-2 py-0.5 rounded">
                        {dept.code}
                      </span>
                    </td>
                    <td className="px-4 py-3"><RoleLabel role={dept.n1Role} /></td>
                    <td className="px-4 py-3">
                      {dept.requiresN2
                        ? <RoleLabel role={dept.n2Role} />
                        : <span className="text-gray-300 text-xs">—</span>}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {dept.requiresN2
                        ? <CheckCircle className="w-4 h-4 text-green-500 mx-auto" />
                        : <XCircle className="w-4 h-4 text-gray-300 mx-auto" />}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-block w-2 h-2 rounded-full ${dept.isActive ? 'bg-green-500' : 'bg-red-400'}`} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>

      <p className="text-xs text-gray-400">
        {t('admin.departments.matrixNote', 'Pour modifier les chaînes d\'approbation, utilisez la Matrice d\'approbation.')}
      </p>
    </div>
  )
}
