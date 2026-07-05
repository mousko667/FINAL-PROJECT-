import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Panel } from "@/components/ui/Panel"
import {  Bell, Check, CheckCheck, X  } from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import {
  markAsRead,
  markAllAsRead,
  type Notification,
} from '@/store/slices/notificationSlice'
import { cn } from '@/lib/utils'
import { formatDateTime } from '@/lib/format'

const typeStyles: Record<Notification['type'], string> = {
  INFO: 'bg-primary/10 border-blue-100 text-primary',
  SUCCESS: 'bg-pos/10 border-green-100 text-pos',
  WARNING: 'bg-amber-50 border-amber-100 text-amber-700',
  ERROR: 'bg-crit/10 border-red-100 text-crit',
}

export function NotificationDropdown() {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const { items, unreadCount } = useAppSelector((s) => s.notifications)
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div className="relative" ref={ref}>
      {/* Bell button */}
      <button
        id="btn-notification-bell"
        onClick={() => setOpen((o) => !o)}
        className="relative p-2 text-ink-soft hover:bg-ground rounded-lg transition-colors"
        aria-label={t('nav.notifications')}
      >
        <Bell className="w-4 h-4" />
        {unreadCount > 0 && (
          <span
            id="notification-badge"
            className="absolute -top-0.5 -right-0.5 bg-crit/100 text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center"
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown panel */}
      {open && (
        <div className="absolute right-0 top-10 w-80 bg-surface rounded-xl shadow-xl border z-50 overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b">
            <h3 className="text-sm font-semibold text-ink">
              {t('notifications.title')}
              {unreadCount > 0 && (
                <span className="ml-2 text-xs text-muted-foreground">
                  ({unreadCount} {t('notifications.unread')})
                </span>
              )}
            </h3>
            <div className="flex items-center gap-1">
              {unreadCount > 0 && (
                <button
                  id="btn-mark-all-read"
                  onClick={() => dispatch(markAllAsRead())}
                  className="p-1.5 text-ink-faint hover:text-primary rounded-lg hover:bg-ground transition-colors"
                  title={t('notifications.markAllRead')}
                >
                  <CheckCheck className="w-4 h-4" />
                </button>
              )}
              <button
                onClick={() => setOpen(false)}
                className="p-1.5 text-ink-faint hover:text-ink-soft rounded-lg hover:bg-ground transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* List */}
          <div className="max-h-80 overflow-y-auto divide-y">
            {items.length === 0 ? (
              <div className="py-10 text-center text-sm text-muted-foreground">
                {t('notifications.noNotifications')}
              </div>
            ) : (
              items.slice(0, 20).map((n) => (
                <div
                  key={n.id}
                  id={`notification-item-${n.id}`}
                  className={cn(
                    'flex items-start gap-3 px-4 py-3 transition-colors cursor-pointer hover:bg-ground',
                    !n.read && 'bg-primary/10/40'
                  )}
                  onClick={() => dispatch(markAsRead(n.id))}
                >
                  <div
                    className={cn(
                      'mt-0.5 px-1.5 py-0.5 rounded text-xs font-medium border shrink-0',
                      typeStyles[n.type]
                    )}
                  >
                    {n.type}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-ink-soft leading-snug">{n.message}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {formatDateTime(n.createdAt)}
                    </p>
                  </div>
                  {!n.read && (
                    <button
                      className="p-1 text-blue-400 hover:text-primary shrink-0"
                      title={t('notifications.markAllRead')}
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
