import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface Notification {
  id: string
  type: 'INFO' | 'WARNING' | 'SUCCESS' | 'ERROR'
  message: string
  invoiceId?: string
  read: boolean
  createdAt: string
}

interface NotificationState {
  items: Notification[]
  unreadCount: number
}

const initialState: NotificationState = {
  items: [],
  unreadCount: 0,
}

const notificationSlice = createSlice({
  name: 'notifications',
  initialState,
  reducers: {
    addNotification: (state, action: PayloadAction<Notification>) => {
      state.items.unshift(action.payload)
      if (!action.payload.read) {
        state.unreadCount += 1
      }
    },
    markAsRead: (state, action: PayloadAction<string>) => {
      const notification = state.items.find((n) => n.id === action.payload)
      if (notification && !notification.read) {
        notification.read = true
        state.unreadCount = Math.max(0, state.unreadCount - 1)
      }
    },
    markAllAsRead: (state) => {
      state.items.forEach((n) => (n.read = true))
      state.unreadCount = 0
    },
    clearNotifications: (state) => {
      state.items = []
      state.unreadCount = 0
    },
  },
})

export const {
  addNotification,
  markAsRead,
  markAllAsRead,
  clearNotifications,
} = notificationSlice.actions
export default notificationSlice.reducer
