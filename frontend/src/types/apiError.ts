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
