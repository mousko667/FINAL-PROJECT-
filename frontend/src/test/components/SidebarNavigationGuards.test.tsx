import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import Sidebar from '@/components/layout/Sidebar'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

const user = (username: string, roles: string[]): AuthUser => ({
  id: '1', username, email: `${username}@oct.fr`, roles,
})

const makeStore = (u: AuthUser) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user: u, accessToken: 'token', refreshToken: null, isAuthenticated: true },
    },
  })

function renderSidebar(u: AuthUser) {
  return render(
    <Provider store={makeStore(u)}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>
          <Sidebar />
        </I18nextProvider>
      </MemoryRouter>
    </Provider>
  )
}

const hrefs = () =>
  [...document.querySelectorAll('a')].map((a) => a.getAttribute('href'))

describe('AUDIT-005 — the "Escalades" entry must reach the DAF', () => {
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('shows the escalation-rules entry to the DAF', () => {
    renderSidebar(user('daf', ['ROLE_DAF']))
    // Nested inside the ADMIN block, its ROLE_DAF branch was dead: the DAF held the
    // right but had no navigation path to it.
    expect(hrefs()).toContain('/admin/escalation-rules')
  })

  it('still shows it to the ADMIN', () => {
    renderSidebar(user('admin', ['ROLE_ADMIN']))
    expect(hrefs()).toContain('/admin/escalation-rules')
  })

  it('does not show it to a role that holds no right on it', () => {
    renderSidebar(user('aa', ['ROLE_ASSISTANT_COMPTABLE']))
    expect(hrefs()).not.toContain('/admin/escalation-rules')

    cleanup()
    renderSidebar(user('val', ['ROLE_VALIDATEUR_N1_DRH']))
    expect(hrefs()).not.toContain('/admin/escalation-rules')
  })

  it('keeps the ADMIN section closed to the DAF (no over-opening)', () => {
    renderSidebar(user('daf', ['ROLE_DAF']))
    const links = hrefs()
    // Moving the entry out of the ADMIN block must not leak the rest of it.
    expect(links).not.toContain('/admin/users')
    expect(links).not.toContain('/admin/security')
    expect(links).not.toContain('/admin/backups')
  })

  it('keeps financial entries closed to the ADMIN (SoD, unchanged by this move)', () => {
    renderSidebar(user('admin', ['ROLE_ADMIN']))
    const links = hrefs()
    expect(links).not.toContain('/invoices')
    expect(links).not.toContain('/reports')
    expect(links).not.toContain('/payments')
    expect(links).not.toContain('/admin/matching-config')
  })
})
