import { useEffect, useRef } from 'react'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { addNotification } from '@/store/slices/notificationSlice'
import type { Notification } from '@/store/slices/notificationSlice'

const WS_URL = 'http://localhost:8080/ws'

export function useWebSocket() {
  const dispatch = useAppDispatch()
  const { user, isAuthenticated, accessToken } = useAppSelector(
    (state) => state.auth
  )
  const clientRef = useRef<Client | null>(null)
  const subscriptionRef = useRef<StompSubscription | null>(null)

  useEffect(() => {
    if (!isAuthenticated || !user || !accessToken) return

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(WS_URL) as unknown as globalThis.WebSocket,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        subscriptionRef.current = client.subscribe(
          `/user/${user.id}/notifications`,
          (message) => {
            try {
              const notification: Notification = JSON.parse(message.body)
              dispatch(addNotification(notification))
            } catch {
              console.error('Failed to parse notification', message.body)
            }
          }
        )
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame)
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      subscriptionRef.current?.unsubscribe()
      client.deactivate()
    }
  }, [isAuthenticated, user, accessToken, dispatch])

  return null
}
