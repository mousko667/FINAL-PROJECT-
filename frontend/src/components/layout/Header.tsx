import { useTranslation } from 'react-i18next'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { logout } from '@/store/slices/authSlice'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { LogOut, Globe, ChevronRight } from 'lucide-react'
import { NotificationDropdown } from './NotificationDropdown'

const BREADCRUMB_MAP: Record<string, string> = {
  '/dashboard':              'nav.dashboard',
  '/invoices':               'nav.invoices',
  '/invoices/new':           'breadcrumb.newInvoice',
  '/approvals':              'nav.approvals',
  '/purchase-orders':        'nav.purchaseOrders',
  '/payments':               'nav.payments',
  '/goods-receipts':         'nav.goodsReceipts',
  '/reports':                'nav.reports',
  '/financial-audit':        'nav.financialAudit',
  '/archive':                'nav.archive',
  '/notifications':          'nav.notifications',
  '/profile':                'nav.profile',
  '/admin/users':            'nav.users',
  '/admin/departments':      'nav.departments',
  '/admin/audit':            'nav.auditLog',
  '/admin/suppliers':        'nav.suppliers',
  '/admin/approval-matrix':  'breadcrumb.approvalMatrix',
  '/admin/security':         'breadcrumb.securitySettings',
  '/admin/integrations':     'admin.integrations.title',
}

function useBreadcrumb() {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const segments: { label: string; href: string }[] = []

  if (pathname === '/dashboard') return segments

  const key = BREADCRUMB_MAP[pathname]
  if (key) {
    segments.push({ label: t(key), href: pathname })
  } else if (pathname.startsWith('/invoices/')) {
    segments.push({ label: t('nav.invoices'), href: '/invoices' })
    segments.push({ label: t('breadcrumb.detail'), href: pathname })
  } else if (pathname.startsWith('/admin/suppliers/')) {
    segments.push({ label: t('nav.suppliers'), href: '/admin/suppliers' })
    segments.push({ label: t('breadcrumb.detail'), href: pathname })
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
    <header className="h-14 border-b border-hairline bg-surface flex items-center justify-between px-6 shrink-0">
      {/* Breadcrumb */}
      <div className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">
        <Link to="/dashboard" className="hover:text-ink transition-colors">
          {t('nav.dashboard')}
        </Link>
        {breadcrumbs.map((crumb, i) => (
          <span key={crumb.href} className="flex items-center gap-1.5">
            <ChevronRight className="w-3.5 h-3.5 text-ink-faint/60" />
            {i === breadcrumbs.length - 1 ? (
              <span className="text-ink font-semibold">{crumb.label}</span>
            ) : (
              <Link to={crumb.href} className="hover:text-ink transition-colors">
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
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-ink-soft hover:bg-ground rounded-[4px] transition-colors border border-transparent hover:border-hairline"
          aria-label="Switch language"
        >
          <Globe className="w-3.5 h-3.5" />
          {i18n.language === 'fr' ? 'EN' : 'FR'}
        </button>

        <NotificationDropdown />

        <div className="flex items-center gap-2 pl-3 ml-1 border-l border-hairline">
          <button
            onClick={() => navigate('/profile')}
            className="flex items-center gap-2 hover:bg-ground rounded-[4px] px-2 py-1.5 transition-colors group"
            aria-label="Profile"
          >
            <div className="w-7 h-7 rounded-full bg-oct-navy flex items-center justify-center shrink-0">
              <span className="text-[10px] font-bold text-oct-gold">{initials}</span>
            </div>
            <span className="text-sm font-medium text-ink-soft group-hover:text-ink hidden sm:block">
              {user?.username}
            </span>
          </button>
          <button
            id="btn-logout"
            onClick={handleLogout}
            className="flex items-center gap-1.5 px-2.5 py-1.5 text-sm text-ink-faint hover:text-crit hover:bg-crit-bg rounded-[4px] transition-colors"
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
