/**
 * Shared shape of a backend error, so reading `error.response.data.message` no longer requires an
 * `as any` cast at every call site (AUDIT-016 — 7 such casts across the pages).
 *
 * The backend wraps every response in `ApiResponse<T>`, so a failed mutation carries an axios error
 * whose `response.data.message` holds either a plain message or an i18n key — callers pass it
 * through `t()` before display (PROB-006).
 */
export interface ApiError {
  response?: {
    status?: number
    data?: {
      message?: string
    }
  }
}

/**
 * Extracts the backend message from an unknown error value, or `undefined` when the error does not
 * carry one (network failure, thrown string, …) so the caller can fall back to a generic label.
 */
export function apiErrorMessage(error: unknown): string | undefined {
  return (error as ApiError | null)?.response?.data?.message
}

/**
 * Translates a backend message before display (AUDIT-015).
 *
 * The backend returns i18n KEYS in some responses (PROB-006) — success confirmations like
 * `payment.recorded.success` and a few error keys. Displaying the extracted message verbatim shows
 * the raw key. This helper runs it through `t()`; when `t(key) === key` (the key is not in the
 * catalog) it falls back to the raw string rather than the bare key, so a backend that already
 * localised the message (via `Accept-Language`) still reads correctly. Returns `undefined` when the
 * error carries no message, so callers keep their generic fallback (`t('app.error')`).
 *
 * This is the single shared implementation of the `t(key) === key ? raw : translated` pattern that
 * was duplicated (or omitted) across the mutation-error sites.
 */
export function translateApiMessage(
  error: unknown,
  t: (key: string) => string,
): string | undefined {
  const raw = apiErrorMessage(error)
  if (!raw) return undefined
  const translated = t(raw)
  return translated === raw ? raw : translated
}
