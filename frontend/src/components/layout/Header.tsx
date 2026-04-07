import { useTranslation } from 'react-i18next'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { logout } from '@/store/slices/authSlice'
import { useNavigate } from 'react-router-dom'
import { Bell, LogOut, Globe } from 'lucide-react'
import { useAppSelector as useNotifSelector } from '@/store/hooks'

export default function Header() {
  const { t, i18n } = useTranslation()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { user } = useAppSelector((s) => s.auth)
  const unreadCount = useNotifSelector((s) => s.notifications.unreadCount)

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  const toggleLanguage = () => {
    const nextLang = i18n.language === 'fr' ? 'en' : 'fr'
    i18n.changeLanguage(nextLang)
  }

  return (
    <header className="h-14 border-b bg-white flex items-center justify-between px-6 shrink-0">
      {/* Breadcrumb placeholder */}
      <div className="text-sm text-muted-foreground" />

      {/* Right actions */}
      <div className="flex items-center gap-3">
        {/* Language switcher */}
        <button
          onClick={toggleLanguage}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          aria-label="Switch language"
        >
          <Globe className="w-4 h-4" />
          {i18n.language === 'fr' ? 'EN' : 'FR'}
        </button>

        {/* Notification bell */}
        <button
          onClick={() => navigate('/notifications')}
          className="relative p-2 text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          aria-label={t('nav.notifications')}
        >
          <Bell className="w-4 h-4" />
          {unreadCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>

        {/* User + logout */}
        <div className="flex items-center gap-2 ml-1 pl-3 border-l">
          <span className="text-sm font-medium text-gray-700">{user?.username}</span>
          <button
            onClick={handleLogout}
            className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            aria-label={t('auth.logout')}
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  )
}
