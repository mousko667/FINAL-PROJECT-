import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import authReducer from '@/store/slices/authSlice'
import type { AuthUser } from '@/store/slices/authSlice'

/**
 * AUDIT-026 — a ROLE_SUPPLIER account must never render a staff screen.
 *
 * The guard used to enumerate the FORBIDDEN prefixes (/admin, /dashboard, /invoices, /reports), so
 * anything outside that list stayed open. The runtime sweep found 4 staff pages reachable by the
 * `supplier` account — /profile, /access-requests, /notifications, /my-delegations — the last two
 * showing an external third party the company's internal role structure and an approval-delegation
 * form. The four are asserted by name below: they are the exact pages the audit reached.
 */

const supplier: AuthUser = {
  id: 's1',
  username: 'supplier',
  email: 'supplier@acme.test',
  roles: ['ROLE_SUPPLIER'],
}

const staffUser: AuthUser = {
  id: 'u1',
  username: 'aa',
  email: 'aa@oct.fr',
  roles: ['ROLE_ASSISTANT_COMPTABLE'],
}

const makeStore = (user: AuthUser) =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: { user, accessToken: 'token', refreshToken: null, isAuthenticated: true },
    },
  })

function renderAt(path: string, user: AuthUser) {
  return render(
    <Provider store={makeStore(user)}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/profile" element={<div>STAFF SCREEN</div>} />
            <Route path="/access-requests" element={<div>STAFF SCREEN</div>} />
            <Route path="/my-delegations" element={<div>STAFF SCREEN</div>} />
            <Route path="/notifications" element={<div>STAFF SCREEN</div>} />
            <Route path="/dashboard" element={<div>STAFF SCREEN</div>} />
          </Route>
          <Route path="/supplier/dashboard" element={<div>SUPPLIER DASHBOARD</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  )
}

afterEach(cleanup)

describe('ProtectedRoute — supplier containment (AUDIT-026)', () => {
  it.each([
    '/profile',
    '/access-requests',
    '/my-delegations',
    '/notifications',
  ])('redirects a SUPPLIER away from %s to the supplier dashboard', (path) => {
    renderAt(path, supplier)

    expect(screen.getByText('SUPPLIER DASHBOARD')).toBeInTheDocument()
    expect(screen.queryByText('STAFF SCREEN')).not.toBeInTheDocument()
  })

  it('keeps redirecting a SUPPLIER away from the routes the old blocklist did cover', () => {
    // Non-regression: inverting the rule must not reopen what already worked.
    renderAt('/dashboard', supplier)

    expect(screen.getByText('SUPPLIER DASHBOARD')).toBeInTheDocument()
  })

  it('still lets a staff account through', () => {
    // Counter-proof: without this, a guard that redirected everyone would pass the tests above.
    renderAt('/profile', staffUser)

    expect(screen.getByText('STAFF SCREEN')).toBeInTheDocument()
  })
})
