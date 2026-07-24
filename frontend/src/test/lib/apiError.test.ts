import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { isNetworkError, hasStatus, getApiErrorKey, getApiErrorMessage } from '@/lib/apiError'

/** An axios rejection with a real HTTP response. */
function httpError(status: number, data?: unknown) {
  const err = new AxiosError('failed')
  err.response = {
    status,
    statusText: '',
    data,
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  }
  return err
}

/** An axios rejection where the request never got an answer (backend down). */
function networkError() {
  const err = new AxiosError('Network Error', 'ERR_NETWORK')
  err.request = {}
  return err
}

describe('AUDIT-014 / AUDIT-035 — telling an outage apart from a 401', () => {
  it('flags a response-less rejection as a network error', () => {
    expect(isNetworkError(networkError())).toBe(true)
  })

  it('does NOT flag a 401 as a network error — this is what wiped the session', () => {
    expect(isNetworkError(httpError(401))).toBe(false)
    expect(isNetworkError(httpError(500))).toBe(false)
  })

  it('hasStatus only matches an effectively received status', () => {
    expect(hasStatus(httpError(404), 404)).toBe(true)
    expect(hasStatus(httpError(401), 404)).toBe(false)
    // The outage must never be read as a 401 — that produced "wrong credentials".
    expect(hasStatus(networkError(), 401)).toBe(false)
  })

  it('maps each failure to its own message key', () => {
    expect(getApiErrorKey(networkError())).toBe('error.network')
    expect(getApiErrorKey(httpError(401))).toBe('error.unauthorized')
    expect(getApiErrorKey(httpError(403))).toBe('error.forbidden')
    expect(getApiErrorKey(httpError(404))).toBe('error.notFound')
    expect(getApiErrorKey(httpError(500))).toBe('error.server')
  })

  it('prefers the backend message when the response carries one', () => {
    expect(getApiErrorMessage(httpError(400, { message: 'Échec de la validation.' })))
      .toBe('Échec de la validation.')
    expect(getApiErrorMessage(httpError(400, { message: '   ' }))).toBeNull()
    expect(getApiErrorMessage(networkError())).toBeNull()
  })
})
