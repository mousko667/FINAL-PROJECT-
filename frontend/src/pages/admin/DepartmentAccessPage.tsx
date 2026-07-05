import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { departmentAccessService, type DepartmentAccess } from '@/services/departmentAccessService'

export default function DepartmentAccessPage() {
  const { t, i18n } = useTranslation()
  const [data, setData] = useState<DepartmentAccess[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    departmentAccessService
      .getOverview()
      .then(setData)
      .catch(() => setError('departmentAccess.error'))
      .finally(() => setLoading(false))
  }, [])

  const name = (d: DepartmentAccess) => (i18n.language === 'en' ? d.nameEn : d.nameFr)

  if (loading) return <div className="p-6">{t('app.loading')}</div>
  if (error) return <div className="p-6 text-red-600">{t(error)}</div>

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{t('departmentAccess.title')}</h1>
        <p className="text-sm text-gray-500">{t('departmentAccess.subtitle')}</p>
      </div>

      {data.length === 0 ? (
        <p className="text-gray-500">{t('departmentAccess.empty')}</p>
      ) : (
        <div className="space-y-3">
          {data.map((d) => (
            <details key={d.departmentId} className="rounded-lg border border-gray-200 bg-white">
              <summary className="cursor-pointer px-4 py-3 flex items-center justify-between">
                <span className="font-medium text-gray-900">
                  <span>{d.code}</span>
                  <span className="text-gray-400"> · </span>
                  <span>{name(d)}</span>
                </span>
                <span className="flex items-center gap-3 text-sm text-gray-600">
                  <span className="rounded bg-gray-100 px-2 py-0.5">
                    {d.requiresN2 ? t('departmentAccess.levelN1N2') : t('departmentAccess.levelN1')}
                  </span>
                  <span>{t('departmentAccess.counts', { total: d.userCount, active: d.activeCount })}</span>
                </span>
              </summary>

              <div className="px-4 pb-4">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b">
                      <th className="py-2">{t('departmentAccess.colName')}</th>
                      <th className="py-2">{t('departmentAccess.colUsername')}</th>
                      <th className="py-2">{t('departmentAccess.colStatus')}</th>
                      <th className="py-2">{t('departmentAccess.colRoles')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {d.users.map((u) => (
                      <tr key={u.userId} className="border-b last:border-0">
                        <td className="py-2 text-gray-900">{u.fullName}</td>
                        <td className="py-2 text-gray-600">{u.username}</td>
                        <td className="py-2">
                          <span className={u.active ? 'text-green-700' : 'text-gray-400'}>
                            {u.active ? t('departmentAccess.active') : t('departmentAccess.inactive')}
                          </span>
                        </td>
                        <td className="py-2">
                          <span className="flex flex-wrap gap-1">
                            {u.roles.map((r) => (
                              <span key={r} className="rounded bg-blue-50 text-blue-700 px-2 py-0.5 text-xs">
                                {r}
                              </span>
                            ))}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          ))}
        </div>
      )}
    </div>
  )
}
