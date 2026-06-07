import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAppSelector } from '@/store/hooks'
import Header from '@/components/layout/Header'
import { useAuth } from '@/hooks/useAuth'
import {
  LayoutDashboard,
  FileText,
  User,
  FolderOpen,
  Container,
  LogOut,
} from 'lucide-react'
import { cn } from '@/lib/utils'

function SupplierNavItem({ to, icon: Icon, label }: { to: string; icon: React.ElementType; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 group',
          isActive
            ? 'bg-oct-navy-light text-white border-l-[3px] border-oct-gold pl-[9px]'
            : 'text-slate-300 hover:bg-white/10 hover:text-white'
        )
      }
    >
      <Icon className="w-4 h-4 shrink-0 transition-transform duration-150 group-hover:scale-105" />
      <span>{label}</span>
    </NavLink>
  )
}

export default function SupplierLayout() {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)
  const { logout } = useAuth()

  const initials = user?.username ? user.username.slice(0, 2).toUpperCase() : '?'

  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 flex flex-col shrink-0 bg-oct-navy text-white shadow-xl">
        {/* Logo */}
        <div className="px-5 py-5 border-b border-white/10">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-oct-gold flex items-center justify-center shrink-0">
              <Container className="w-4 h-4 text-oct-navy" />
            </div>
            <div className="min-w-0">
              <h1 className="text-sm font-bold text-white leading-tight">OCT Invoices</h1>
              <p className="text-[10px] text-oct-gold leading-tight">Portail Fournisseur</p>
            </div>
          </div>

          {user && (
            <div className="mt-3.5 px-2 py-2 rounded-lg bg-white/5 border border-white/10 flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-oct-gold flex items-center justify-center shrink-0">
                <span className="text-[10px] font-bold text-oct-navy">{initials}</span>
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold text-white truncate">{user.username}</p>
                <p className="text-[10px] text-slate-400">Fournisseur</p>
              </div>
            </div>
          )}
        </div>

        <nav className="flex-1 px-3 py-3 space-y-0.5 overflow-y-auto">
          <SupplierNavItem to="/supplier/dashboard" icon={LayoutDashboard} label={t('supplier.portal.dashboard', 'Mon tableau de bord')} />
          <SupplierNavItem to="/supplier/invoices" icon={FileText} label={t('supplier.portal.invoices', 'Mes Factures')} />
          <SupplierNavItem to="/supplier/profile" icon={User} label={t('supplier.portal.profile', 'Mon Profil')} />
          <SupplierNavItem to="/supplier/documents" icon={FolderOpen} label={t('supplier.portal.documents', 'Mes Documents')} />
        </nav>

        <div className="px-4 py-3 border-t border-white/10">
          <button
            onClick={logout}
            className="flex items-center gap-2 text-xs text-slate-400 hover:text-red-400 transition-colors w-full py-1.5"
          >
            <LogOut className="w-3.5 h-3.5" />
            {t('auth.logout')}
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
