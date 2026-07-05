import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { ROLE_OPTIONS } from '@/constants/roles'
import { Loader2, Check, ShieldCheck, AlertCircle } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'

interface User {
  id: string
  username: string
  email: string
  firstName?: string
  lastName?: string
  fullName?: string
  roles: string[]
  isActive?: boolean
  active?: boolean
}

interface Role {
  id: string
  name: string
  description?: string
}

/**
 * P11-18 — visual permission-matrix editor (REQ-23 item 4). A users × roles grid of
 * checkboxes built entirely over existing endpoints: reads via GET /users, writes via
 * PUT /users/{id}/roles (replaces the user's roles). Edits are staged per row and persisted
 * with an explicit per-row Save button (one PUT with the final role list). ADMIN only.
 */
export default function AdminPermissionMatrixPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  // Staged role edits per user id: present only for rows the admin has touched.
  const [draft, setDraft] = useState<Record<string, Set<string>>>({})
  const [savedId, setSavedId] = useState<string | null>(null)
  const [errorId, setErrorId] = useState<string | null>(null)

  const { data: users = [], isLoading: usersLoading } = useQuery<User[]>({
    queryKey: ['admin', 'permission-matrix', 'users'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<User>>>('/users', { params: { size: 200 } })
      // ROLE_SUPPLIER users are managed via the supplier portal, not assignable here.
      return (data.data.content ?? []).filter(u => !u.roles.includes('ROLE_SUPPLIER'))
    },
  })

  // Role name → UUID map. PUT /users/{id}/roles takes role UUIDs (AssignRoleRequest.roleIds).
  const { data: roleList = [], isLoading: rolesLoading } = useQuery<Role[]>({
    queryKey: ['admin', 'roles'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Role[]>>('/roles')
      return data.data ?? []
    },
  })
  const roleNameToId = useMemo(
    () => new Map(roleList.map(r => [r.name, r.id])),
    [roleList],
  )

  const isLoading = usersLoading || rolesLoading

  const saveRoles = useMutation({
    mutationFn: ({ id, roleNames }: { id: string; roleNames: string[] }) => {
      const roleIds = roleNames
        .map(name => roleNameToId.get(name))
        .filter((rid): rid is string => Boolean(rid))
      return apiClient.put(`/users/${id}/roles`, { roleIds })
    },
    onSuccess: (_res, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'permission-matrix', 'users'] })
      setDraft(prev => { const next = { ...prev }; delete next[id]; return next })
      setErrorId(null)
      setSavedId(id)
      setTimeout(() => setSavedId(s => (s === id ? null : s)), 2500)
    },
    onError: (_err, { id }) => setErrorId(id),
  })

  // Current (possibly staged) role set for a user.
  const rolesOf = (u: User): Set<string> => draft[u.id] ?? new Set(u.roles)

  const isDirty = (u: User): boolean => {
    const d = draft[u.id]
    if (!d) return false
    const current = new Set(u.roles)
    if (d.size !== current.size) return true
    for (const r of d) if (!current.has(r)) return true
    return false
  }

  const toggle = (u: User, role: string) => {
    setSavedId(null)
    setErrorId(null)
    setDraft(prev => {
      const base = prev[u.id] ?? new Set(u.roles)
      const next = new Set(base)
      if (next.has(role)) next.delete(role)
      else next.add(role)
      return { ...prev, [u.id]: next }
    })
  }

  const displayName = (u: User) =>
    u.fullName || [u.firstName, u.lastName].filter(Boolean).join(' ') || u.username

  const sortedUsers = useMemo(
    () => [...users].sort((a, b) => displayName(a).localeCompare(displayName(b))),
    [users],
  )

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3">
        <ShieldCheck className="w-6 h-6 text-primary mt-0.5" />
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('admin.permissions.title')}</h1>
          <p className="text-sm text-ink-soft mt-1">{t('admin.permissions.subtitle')}</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-10"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
      ) : (
        <Panel className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                <th className="sticky left-0 z-10 text-left px-4 py-3 min-w-[180px] bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">
                  {t('admin.permissions.user')}
                </th>
                {ROLE_OPTIONS.map(role => (
                  <th key={role.value} className="px-2 py-3 text-center align-bottom bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint" title={role.label}>
                    <span className="inline-block text-[11px] font-medium text-ink-faint whitespace-nowrap">
                      {t(`roles.${role.value}`, role.short)}
                    </span>
                  </th>
                ))}
                <th className="px-4 py-3 text-right min-w-[120px] bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">
                  {t('admin.permissions.action')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {sortedUsers.map(u => {
                const roles = rolesOf(u)
                const dirty = isDirty(u)
                return (
                  <tr key={u.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                    <td className="sticky left-0 z-10 bg-surface px-4 py-2">
                      <div className="font-medium text-ink">{displayName(u)}</div>
                      <div className="text-xs text-ink-faint">{u.username}</div>
                    </td>
                    {ROLE_OPTIONS.map(role => {
                      const checked = roles.has(role.value)
                      return (
                        <td key={role.value} className="px-2 py-2 text-center">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggle(u, role.value)}
                            className="w-4 h-4 accent-primary cursor-pointer"
                            aria-label={`${displayName(u)} — ${role.label}`}
                          />
                        </td>
                      )
                    })}
                    <td className="px-4 py-2 text-right">
                      {savedId === u.id ? (
                        <span className="inline-flex items-center gap-1 text-xs text-pos">
                          <Check className="w-3.5 h-3.5" /> {t('admin.permissions.saved')}
                        </span>
                      ) : errorId === u.id ? (
                        <span className="inline-flex items-center gap-1 text-xs text-crit">
                          <AlertCircle className="w-3.5 h-3.5" /> {t('admin.permissions.saveError')}
                        </span>
                      ) : (
                        <button
                          onClick={() => saveRoles.mutate({ id: u.id, roleNames: Array.from(roles) })}
                          disabled={!dirty || saveRoles.isPending}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-primary text-primary-foreground rounded-[4px] text-xs font-medium hover:bg-primary/90 disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {saveRoles.isPending && saveRoles.variables?.id === u.id && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                          {t('admin.permissions.save')}
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  )
}
