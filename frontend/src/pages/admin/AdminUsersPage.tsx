import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2 } from 'lucide-react'

interface User {
  id: string
  username: string
  email: string  
  fullName: string
  roles: string[]
  department?: { name: string }
  active: boolean
}

export default function AdminUsersPage() {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<User>>>('/users')
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">{t('admin.users.title')}</h1>

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.fullName')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.email')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.role')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.department')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.active')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(!data?.content || data.content.length === 0) ? (
                <tr><td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td></tr>
              ) : data.content.map((user) => (
                <tr key={user.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{user.fullName}</td>
                  <td className="px-4 py-3 text-gray-500">{user.email}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {user.roles.map((r) => (
                        <span key={r} className="text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">{r.replace('ROLE_', '')}</span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-500">{user.department?.name ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${user.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                      {user.active ? '✓' : '✗'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
