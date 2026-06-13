import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { logout, updateToken } from '@/store/slices/authSlice'
import { BASE_URL } from '@/services/apiClient'

const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart']
const IDLE_CHECK_MS = 30_000

/**
 * P11-40 #1 — real inactivity timeout. The frontend is the precise authority: after
 * `sessionTimeoutMinutes` without user activity, it signs the user out. While the user IS
 * active, it proactively refreshes (at half the timeout) so the server-side session keeps
 * sliding forward and an active user is never logged out mid-session. The server enforces
 * the same window as a guard (the refresh is rejected once the session has expired).
 */
export function useSessionTimeout() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const isAuthenticated = useAppSelector((s) => s.auth.isAuthenticated)
  const timeoutMinutes = useAppSelector((s) => s.auth.sessionTimeoutMinutes)
  const lastActivity = useRef(Date.now())

  useEffect(() => {
    if (!isAuthenticated || !timeoutMinutes) return
    const timeoutMs = timeoutMinutes * 60_000
    lastActivity.current = Date.now()

    const onActivity = () => { lastActivity.current = Date.now() }
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }))

    const signOut = () => {
      dispatch(logout())
      navigate('/login', { replace: true })
    }

    // Idle check: sign out once the user has been inactive for the whole timeout.
    const idleCheck = window.setInterval(() => {
      if (Date.now() - lastActivity.current >= timeoutMs) signOut()
    }, IDLE_CHECK_MS)

    // Proactive refresh while active, so the server session never expires under an active user.
    const refreshTick = window.setInterval(async () => {
      if (Date.now() - lastActivity.current >= timeoutMs) return // inactive → idleCheck handles it
      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) return
      try {
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken })
        if (data?.data?.accessToken) dispatch(updateToken(data.data.accessToken))
      } catch {
        signOut() // session rejected server-side (expired/revoked) → sign out
      }
    }, Math.max(timeoutMs / 2, 60_000))

    return () => {
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity))
      window.clearInterval(idleCheck)
      window.clearInterval(refreshTick)
    }
  }, [isAuthenticated, timeoutMinutes, dispatch, navigate])
}
