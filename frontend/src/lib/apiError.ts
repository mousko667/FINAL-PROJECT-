import type { AxiosError } from 'axios'

/**
 * Tells a server outage apart from an authentication rejection.
 *
 * AUDIT-014 / AUDIT-035: when the backend is down, axios rejects with **no
 * `response`** at all. Code that only looked at "did the call fail" treated that
 * as a 401 — the session was wiped and the login screen claimed the credentials
 * were wrong. Only a response that actually carries a 401 means "not authenticated".
 */
export function isNetworkError(error: unknown): boolean {
  const e = error as AxiosError | undefined
  if (!e || typeof e !== 'object') return false
  // A rejection with a config but no response never reached the server.
  return !e.response && (e.code === 'ERR_NETWORK' || e.code === 'ECONNABORTED' || !!e.request)
}

/** True only for an HTTP response effectively carrying the given status. */
export function hasStatus(error: unknown, status: number): boolean {
  return (error as AxiosError | undefined)?.response?.status === status
}

/**
 * i18n key describing an API failure, for display to the user.
 *
 * Never returns a raw backend string: `error.server` / `error.network` are
 * present in both catalogs. The backend message, when it carries one, is
 * returned as-is by {@link getApiErrorMessage} — the backend already localises
 * it via `Accept-Language`.
 */
export function getApiErrorKey(error: unknown): string {
  if (isNetworkError(error)) return 'error.network'
  if (hasStatus(error, 401)) return 'error.unauthorized'
  if (hasStatus(error, 403)) return 'error.forbidden'
  if (hasStatus(error, 404)) return 'error.notFound'
  return 'error.server'
}

type BackendBody = { message?: unknown; error?: unknown }

/**
 * Backend message when the response carries one, otherwise `null` so the caller
 * falls back to {@link getApiErrorKey}.
 */
export function getApiErrorMessage(error: unknown): string | null {
  const data = (error as AxiosError<BackendBody> | undefined)?.response?.data
  if (!data || typeof data !== 'object') return null
  const msg = data.message ?? data.error
  return typeof msg === 'string' && msg.trim().length > 0 ? msg : null
}
