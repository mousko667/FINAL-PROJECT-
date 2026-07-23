import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import Header from '@/components/layout/Header'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

const daf: AuthUser = {
  id: '1',
  username: 'daf',
  email: 'daf@oct.fr',
  roles: ['ROLE_DAF'],
}

const makeStore = () =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: {
        user: daf,
        accessToken: 'token',
        refreshToken: null,
        isAuthenticated: true,
      },
    },
  })

function renderHeader(path = '/dashboard') {
  return render(
    <Provider store={makeStore()}>
      <MemoryRouter initialEntries={[path]}>
        <I18nextProvider i18n={i18n}>
          <Header />
        </I18nextProvider>
      </MemoryRouter>
    </Provider>
  )
}

describe('Header', () => {
  // changeLanguage is async and i18n is shared across test files: without awaiting, the reset can
  // land after the next test has rendered (intermittent failures).
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('renders the French dashboard breadcrumb by default', () => {
    renderHeader('/dashboard')
    expect(screen.getByText('Tableau de bord')).toBeInTheDocument()
  })

  it('renders English breadcrumb labels when i18n.language is en (not the frozen FR labels)', async () => {
    await i18n.changeLanguage('en')
    renderHeader('/invoices')
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Invoices')).toBeInTheDocument()
    expect(screen.queryByText('Tableau de bord')).toBeNull()
    expect(screen.queryByText('Factures')).toBeNull()
  })

  it('resolves the /invoices/new breadcrumb via the new breadcrumb.newInvoice key in English', async () => {
    await i18n.changeLanguage('en')
    renderHeader('/invoices/new')
    expect(screen.getByText('New invoice')).toBeInTheDocument()
    expect(screen.queryByText('Nouvelle facture')).toBeNull()
  })
})
