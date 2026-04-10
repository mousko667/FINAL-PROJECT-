import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2 } from 'lucide-react'

interface Department {
  id: string
  name: string
  code: string
  budget: number
  currency: string
  headCount?: number
}

export default function AdminDepartmentsPage() {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<Department>>>('/departments')
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">{t('admin.departments.title')}</h1>

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.departments.name')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.departments.code')}</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">{t('admin.departments.budget')}</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">Effectif</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(!data?.content || data.content.length === 0) ? (
                <tr>
                  <td colSpan={4} className="text-center py-16 text-muted-foreground">
                    {t('app.noData')}
                  </td>
                </tr>
              ) : (
                data.content.map((dept) => (
                  <tr key={dept.id} id={`dept-row-${dept.id}`} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-800">{dept.name}</td>
                    <td className="px-4 py-3">
                      <span className="text-xs font-mono bg-gray-100 text-gray-700 px-2 py-0.5 rounded">
                        {dept.code}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-gray-700">
                      {dept.budget?.toLocaleString()} {dept.currency}
                    </td>
                    <td className="px-4 py-3 text-right text-gray-500">
                      {dept.headCount ?? '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
