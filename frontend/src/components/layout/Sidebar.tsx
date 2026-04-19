import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAppSelector } from '@/store/hooks'
import { RoleGuard } from '@/components/auth/RoleGuard'
import {
  LayoutDashboard,
  FileText,
  BarChart3,
  Users,
  Building2,
  ScrollText,
  ChevronRight,
  Truck,
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm font-medium transition-colors',
    isActive
      ? 'bg-primary text-primary-foreground'
      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
  )

export default function Sidebar() {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)

  return (
    <aside className="w-64 border-r bg-white flex flex-col">
      {/* Logo */}
      <div className="px-6 py-5 border-b">
        <h1 className="text-lg font-bold text-primary">{t('app.name')}</h1>
        {user && (
          <p className="text-xs text-muted-foreground mt-0.5 truncate">
            {user.username}
          </p>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <NavLink to="/dashboard" className={navLinkClass}>
          <LayoutDashboard className="w-4 h-4" />
          {t('nav.dashboard')}
        </NavLink>

        <NavLink to="/invoices" className={navLinkClass}>
          <FileText className="w-4 h-4" />
          {t('nav.invoices')}
        </NavLink>

        <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']}>
          <NavLink to="/reports" className={navLinkClass}>
            <BarChart3 className="w-4 h-4" />
            {t('nav.reports')}
          </NavLink>
        </RoleGuard>

        <RoleGuard allowedRoles={['ROLE_ADMIN']}>
          <div className="pt-4">
            <p className="px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              {t('nav.admin')}
            </p>
            <NavLink to="/admin/users" className={navLinkClass}>
              <Users className="w-4 h-4" />
              {t('nav.users')}
            </NavLink>
            <NavLink to="/admin/departments" className={navLinkClass}>
              <Building2 className="w-4 h-4" />
              {t('nav.departments')}
            </NavLink>
            <NavLink to="/admin/audit" className={navLinkClass}>
              <ScrollText className="w-4 h-4" />
              {t('nav.auditLog')}
            </NavLink>
            <NavLink to="/admin/suppliers" className={navLinkClass}>
              <Truck className="w-4 h-4" />
              {t('nav.suppliers', 'Fournisseurs')}
            </NavLink>
          </div>
        </RoleGuard>
      </nav>

      {/* Version */}
      <div className="px-4 py-3 border-t text-xs text-muted-foreground flex items-center justify-between">
        <span>v1.0.0</span>
        <ChevronRight className="w-3 h-3" />
      </div>
    </aside>
  )
}
