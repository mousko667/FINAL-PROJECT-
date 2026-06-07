import { useTranslation } from 'react-i18next'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { logout } from '@/store/slices/authSlice'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { LogOut, Globe, ChevronRight } from 'lucide-react'
import { NotificationDropdown } from './NotificationDropdown'

const BREADCRUMB_MAP: Record<string, string> = {
  '/dashboard':              'Tableau de bord',
  '/invoices':               'Factures',
  '/invoices/new':           'Nouvelle facture',
  '/approvals':              'File d\'approbation',
  '/purchase-orders':        'Bons de commande',
  '/payments':               'Paiements',
  '/goods-receipts':         'Bons de réception',
  '/reports':                'Rapports',
  '/financial-audit':        'Audit financier',
  '/archive':                'Archive numérique',
  '/notifications':          'Notifications',
  '/profile':                'Mon profil',
  '/admin/users':            'Utilisateurs',
  '/admin/departments':      'Départements',
  '/admin/audit':            'Journal d\'audit',
  '/admin/suppliers':        'Fournisseurs',
  '/admin/approval-matrix':  'Matrice d\'approbation',
  '/admin/security':         'Paramètres de sécurité',
  '/admin/integrations':     'Intégrations',
}

function useBreadcrumb() {
  const { pathname } = useLocation()
  const segments: { label: string; href: string }[] = []

  if (pathname === '/dashboard') return segments

  const label = BREADCRUMB_MAP[pathname]
  if (label) {
    segments.push({ label, href: pathname })
  } else if (pathname.startsWith('/invoices/')) {
    segments.push({ label: 'Factures', href: '/invoices' })
    segments.push({ label: 'Détail', href: pathname })
  } else if (pathname.startsWith('/admin/suppliers/')) {
    segments.push({ label: 'Fournisseurs', href: '/admin/suppliers' })
    segments.push({ label: 'Détail', href: pathname })
  }

  return segments
}

export default function Header() {
  const { t, i18n } = useTranslation()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { user } = useAppSelector((s) => s.auth)
  const breadcrumbs = useBreadcrumb()

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'fr' ? 'en' : 'fr')
  }

  const initials = user?.username
    ? user.username.slice(0, 2).toUpperCase()
    : '??'

  return (
    <header className="h-14 border-b bg-white flex items-center justify-between px-6 shrink-0">
      {/* Breadcrumb */}
      <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
        <Link to="/dashboard" className="hover:text-foreground transition-colors">
          Tableau de bord
        </Link>
        {breadcrumbs.map((crumb, i) => (
          <span key={crumb.href} className="flex items-center gap-1.5">
            <ChevronRight className="w-3.5 h-3.5 text-gray-300" />
            {i === breadcrumbs.length - 1 ? (
              <span className="text-foreground font-medium">{crumb.label}</span>
            ) : (
              <Link to={crumb.href} className="hover:text-foreground transition-colors">
                {crumb.label}
              </Link>
            )}
          </span>
        ))}
      </div>

      {/* Right actions */}
      <div className="flex items-center gap-2">
        <button
          id="btn-language-switcher"
          onClick={toggleLanguage}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-gray-500 hover:bg-gray-100 rounded-lg transition-colors border border-transparent hover:border-gray-200"
          aria-label="Switch language"
        >
          <Globe className="w-3.5 h-3.5" />
          {i18n.language === 'fr' ? 'EN' : 'FR'}
        </button>

        <NotificationDropdown />

        <div className="flex items-center gap-2 pl-3 ml-1 border-l">
          <button
            onClick={() => navigate('/profile')}
            className="flex items-center gap-2 hover:bg-gray-100 rounded-lg px-2 py-1.5 transition-colors group"
            aria-label="Profile"
          >
            <div className="w-7 h-7 rounded-full bg-oct-navy flex items-center justify-center shrink-0">
              <span className="text-[10px] font-bold text-oct-gold">{initials}</span>
            </div>
            <span className="text-sm font-medium text-gray-700 group-hover:text-gray-900 hidden sm:block">
              {user?.username}
            </span>
          </button>
          <button
            id="btn-logout"
            onClick={handleLogout}
            className="flex items-center gap-1.5 px-2.5 py-1.5 text-sm text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            aria-label={t('auth.logout')}
            title={t('auth.logout')}
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  )
}
