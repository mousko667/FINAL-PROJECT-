import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { markAllAsRead, markAsRead } from '@/store/slices/notificationSlice'
import apiClient from '@/services/apiClient'
import {
  CheckCheck, Info, AlertTriangle, CheckCircle, XCircle,
  FileText, Loader2, BellOff, FileInput, CreditCard, Clock,
} from 'lucide-react'
import { formatDateTime } from '@/lib/format'
import { PageHeader } from '@/components/ui/PageHeader'
import { notifyApiError } from '@/components/ErrorToaster'

/**
 * Raw notification shape returned by GET /notifications (see NotificationDTO on the
 * backend). Titles and messages are bilingual; the read flag is `isRead`.
 */
interface ApiNotification {
  id: string
  invoiceId?: string
  titleFr?: string
  titleEn?: string
  messageFr?: string
  messageEn?: string
  type: string
  isRead: boolean
  createdAt: string
}

/** Normalised, language-resolved notification used by the view. */
interface ViewNotification {
  id: string
  type: string
  title: string
  message: string
  invoiceId?: string
  read: boolean
  createdAt: string
}

/**
 * Maps the business notification type to an icon. Types come from the backend
 * (SUBMISSION | VALIDATION | REJECTION | APPROVAL | PAYMENT | DEADLINE) as well as the
 * generic WS levels (SUCCESS | WARNING | ERROR | INFO).
 */
function NotifIcon({ type }: { type: string }) {
  switch (type) {
    case 'APPROVAL':
    case 'SUCCESS':    return <CheckCircle className="w-5 h-5 text-pos shrink-0" />
    case 'REJECTION':
    case 'ERROR':      return <XCircle className="w-5 h-5 text-crit shrink-0" />
    case 'DEADLINE':
    case 'WARNING':    return <AlertTriangle className="w-5 h-5 text-warn shrink-0" />
    case 'PAYMENT':    return <CreditCard className="w-5 h-5 text-info shrink-0" />
    case 'SUBMISSION': return <FileInput className="w-5 h-5 text-info shrink-0" />
    case 'VALIDATION': return <Clock className="w-5 h-5 text-info shrink-0" />
    default:           return <Info className="w-5 h-5 text-info shrink-0" />
  }
}

export default function NotificationsPage() {
  const { t, i18n } = useTranslation()
  const dispatch = useAppDispatch()
  const queryClient = useQueryClient()
  const storeNotifs = useAppSelector(s => s.notifications.items)
  const isFr = i18n.language?.startsWith('fr')

  // Load from backend (persistent) and merge with in-memory WS notifications.
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

  // Resolve each persisted notification to the current UI language.
  const persisted: ViewNotification[] = (apiNotifs ?? []).map(n => ({
    id: n.id,
    type: n.type,
    title: (isFr ? n.titleFr : n.titleEn) ?? n.titleFr ?? n.titleEn ?? '',
    message: (isFr ? n.messageFr : n.messageEn) ?? n.messageFr ?? n.messageEn ?? '',
    invoiceId: n.invoiceId,
    read: n.isRead,
    createdAt: n.createdAt,
  }))

  // In-memory WS notifications carry a single `message`/`read` (see notificationSlice);
  // fold them in, deduplicated against the persisted set.
  const wsOnly: ViewNotification[] = storeNotifs
    .filter(n => !persisted.some(a => a.id === n.id))
    .map(n => ({
      id: n.id,
      type: n.type,
      title: '',
      message: n.message,
      invoiceId: n.invoiceId,
      read: n.read,
      createdAt: n.createdAt,
    }))

  const allNotifs = [...persisted, ...wsOnly]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  const unreadNotifs = allNotifs.filter(n => !n.read)
  const readNotifs = allNotifs.filter(n => n.read)
  const unread = unreadNotifs.length

  const handleMarkRead = (n: ViewNotification) => {
    if (n.read) return
    dispatch(markAsRead(n.id))
    markReadMutation.mutate(n.id)
  }

  const renderRow = (notif: ViewNotification) => (
    <div
      key={notif.id}
      className={`flex items-start gap-4 px-5 py-4 transition-colors ${
        !notif.read ? 'bg-info-bg/40 cursor-pointer hover:bg-ground' : 'hover:bg-ground'
      }`}
      onClick={() => handleMarkRead(notif)}
    >
      <div className="mt-0.5">
        <NotifIcon type={notif.type} />
      </div>

      <div className="flex-1 min-w-0">
        {notif.title && (
          <p className={`text-sm ${!notif.read ? 'font-semibold text-ink' : 'font-medium text-ink-soft'}`}>
            {notif.title}
          </p>
        )}
        {notif.message && (
          <p className={`text-sm ${notif.title ? 'text-ink-soft mt-0.5' : !notif.read ? 'font-semibold text-ink' : 'text-ink-soft'}`}>
            {notif.message}
          </p>
        )}
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
  )

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
            className="flex items-center gap-2 px-4 py-2 border border-white/40 text-white rounded-[4px] text-sm font-medium hover:bg-white/10 transition-colors disabled:opacity-60"
          >
            {markAllMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCheck className="w-4 h-4" />}
            {t('notifications.markAllRead', 'Tout marquer comme lu')}
          </button>
        )}
      />

      {isLoading ? (
        <div className="bg-surface rounded-[4px] border border-hairline flex justify-center py-16">
          <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
        </div>
      ) : allNotifs.length === 0 ? (
        <div className="bg-surface rounded-[4px] border border-hairline flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
          <BellOff className="w-10 h-10" />
          <p className="text-sm font-medium">{t('notifications.noNotifications', 'Aucune notification')}</p>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Unread section */}
          {unreadNotifs.length > 0 && (
            <section>
              <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-faint mb-2 px-1">
                {t('notifications.sectionUnread', 'Non lues')} ({unreadNotifs.length})
              </h2>
              <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden divide-y">
                {unreadNotifs.map(renderRow)}
              </div>
            </section>
          )}

          {/* Read section */}
          {readNotifs.length > 0 && (
            <section>
              <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-faint mb-2 px-1">
                {t('notifications.sectionRead', 'Lues')} ({readNotifs.length})
              </h2>
              <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden divide-y opacity-90">
                {readNotifs.map(renderRow)}
              </div>
            </section>
          )}
        </div>
      )}

      {unread > 0 && (
        <p className="text-xs text-ink-faint text-center">
          {t('notifications.hint', 'Cliquez sur une notification pour la marquer comme lue.')}
        </p>
      )}
    </div>
  )
}
