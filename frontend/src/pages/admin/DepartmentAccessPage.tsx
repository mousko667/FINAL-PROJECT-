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
  if (error) return <div className="p-6 text-crit">{t(error)}</div>

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-bold text-ink">{t('departmentAccess.title')}</h1>
        <p className="text-sm text-ink-faint">{t('departmentAccess.subtitle')}</p>
      </div>

      {data.length === 0 ? (
        <p className="text-ink-faint">{t('departmentAccess.empty')}</p>
      ) : (
        <div className="space-y-3">
          {data.map((d) => (
            <details key={d.departmentId} className="rounded-[4px] border border-hairline bg-surface">
              <summary className="cursor-pointer px-4 py-3 flex items-center justify-between">
                <span className="font-medium text-ink">
                  <span>{d.code}</span>
                  <span className="text-ink-faint"> · </span>
                  <span>{name(d)}</span>
                </span>
                <span className="flex items-center gap-3 text-sm text-ink-soft">
                  <span className="rounded bg-ground px-2 py-0.5">
                    {d.requiresN2 ? t('departmentAccess.levelN1N2') : t('departmentAccess.levelN1')}
                  </span>
                  <span>{t('departmentAccess.counts', { total: d.userCount, active: d.activeCount })}</span>
                </span>
              </summary>

              <div className="px-4 pb-4">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-ink-faint hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                      <th className="py-2 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('departmentAccess.colName')}</th>
                      <th className="py-2 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('departmentAccess.colUsername')}</th>
                      <th className="py-2 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('departmentAccess.colStatus')}</th>
                      <th className="py-2 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('departmentAccess.colRoles')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-hairline">
                    {d.users.map((u) => (
                      <tr key={u.userId} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                        <td className="py-2 text-ink">{u.fullName}</td>
                        <td className="py-2 text-ink-soft">{u.username}</td>
                        <td className="py-2">
                          <span className={u.active ? 'text-pos' : 'text-ink-faint'}>
                            {u.active ? t('departmentAccess.active') : t('departmentAccess.inactive')}
                          </span>
                        </td>
                        <td className="py-2">
                          <span className="flex flex-wrap gap-1">
                            {u.roles.map((r) => (
                              <span key={r} className="rounded bg-primary/10 text-primary px-2 py-0.5 text-xs">
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
