import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { markAllAsRead, markAsRead } from '@/store/slices/notificationSlice'
import apiClient from '@/services/apiClient'
import {
  Bell, CheckCheck, Info, AlertTriangle, CheckCircle, XCircle,
  FileText, Loader2, BellOff,
} from 'lucide-react'
import { formatDateTime } from '@/lib/format'
import { PageHeader } from '@/components/ui/PageHeader'
import { notifyApiError } from '@/components/ErrorToaster'

interface ApiNotification {
  id: string
  type: string
  message: string
  invoiceId?: string
  read: boolean
  createdAt: string
}

function NotifIcon({ type }: { type: string }) {
  switch (type) {
    case 'SUCCESS': return <CheckCircle className="w-5 h-5 text-pos shrink-0" />
    case 'WARNING': return <AlertTriangle className="w-5 h-5 text-warn shrink-0" />
    case 'ERROR':   return <XCircle className="w-5 h-5 text-crit shrink-0" />
    default:        return <Info className="w-5 h-5 text-info shrink-0" />
  }
}

export default function NotificationsPage() {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const queryClient = useQueryClient()
  const storeNotifs = useAppSelector(s => s.notifications.items)

  // Load from backend (persistent) and merge with in-memory WS notifications
  const { data: apiNotifs, isLoading } = useQuery({
    queryKey: ['notifications-page'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: ApiNotification[] } }>(
        '/notifications', { params: { size: 100 } }
      )
      return data.data?.content ?? []
    },
  })

  const markReadMutation = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: (id: string) => apiClient.patch(`/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications-page'] }),
  })

  const markAllMutation = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.patch('/notifications/read-all'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-page'] })
      dispatch(markAllAsRead())
    },
  })

  // Merge: backend notifs + in-memory WS notifs (dedup by id)
  const allNotifs: ApiNotification[] = [
    ...(apiNotifs ?? []),
    ...storeNotifs.filter(n => !(apiNotifs ?? []).some(a => a.id === n.id)).map(n => ({
      id: n.id,
      type: n.type,
      message: n.message,
      invoiceId: n.invoiceId,
      read: n.read,
      createdAt: n.createdAt,
    })),
  ].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  const unread = allNotifs.filter(n => !n.read).length

  const handleMarkRead = (n: ApiNotification) => {
    if (n.read) return
    dispatch(markAsRead(n.id))
    markReadMutation.mutate(n.id)
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <PageHeader
        title={t('notifications.title', 'Notifications')}
        subtitle={unread > 0 && (
          <>
            <span className="font-semibold text-white">{unread}</span> {t('notifications.unread', 'non lu(s)')}
          </>
        )}
        actions={unread > 0 && (
          <button
            onClick={() => markAllMutation.mutate()}
            disabled={markAllMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm font-medium hover:bg-ground transition-colors disabled:opacity-60"
          >
            {markAllMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCheck className="w-4 h-4" />}
            {t('notifications.markAllRead', 'Tout marquer comme lu')}
          </button>
        )}
      />

      <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : allNotifs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
            <BellOff className="w-10 h-10" />
            <p className="text-sm font-medium">{t('notifications.noNotifications', 'Aucune notification')}</p>
          </div>
        ) : (
          <div className="divide-y">
            {allNotifs.map(notif => (
              <div
                key={notif.id}
                className={`flex items-start gap-4 px-5 py-4 cursor-pointer hover:bg-ground transition-colors ${
                  !notif.read ? 'bg-info-bg/40' : ''
                }`}
                onClick={() => handleMarkRead(notif)}
              >
                <div className="mt-0.5">
                  <NotifIcon type={notif.type} />
                </div>

                <div className="flex-1 min-w-0">
                  <p className={`text-sm ${!notif.read ? 'font-semibold text-ink' : 'text-ink-soft'}`}>
                    {notif.message}
                  </p>
                  <div className="flex items-center gap-3 mt-1">
                    <span className="text-xs text-ink-faint">
                      {formatDateTime(notif.createdAt)}
                    </span>
                    {notif.invoiceId && (
                      <Link
                        to={`/invoices/${notif.invoiceId}`}
                        onClick={e => e.stopPropagation()}
                        className="flex items-center gap-1 text-xs text-primary hover:underline font-medium"
                      >
                        <FileText className="w-3 h-3" />
                        {t('app.view', 'Voir la facture')}
                      </Link>
                    )}
                  </div>
                </div>

                {!notif.read && (
                  <div className="w-2 h-2 bg-primary rounded-full shrink-0 mt-2" />
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <p className="text-xs text-ink-faint text-center">
        {t('notifications.hint', 'Cliquez sur une notification pour la marquer comme lue.')}
      </p>
    </div>
  )
}
