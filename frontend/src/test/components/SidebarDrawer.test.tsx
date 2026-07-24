import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import Header from '@/components/layout/Header'
import { SidebarShell } from '@/components/layout/SidebarShell'
import { SidebarDrawerProvider } from '@/components/layout/SidebarDrawerContext'
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
      auth: { user: daf, accessToken: 'token', refreshToken: null, isAuthenticated: true },
    },
  })

/** Renders the same pairing both layouts use: one SidebarShell + the shared Header. */
function renderLayout(path = '/dashboard') {
  return render(
    <Provider store={makeStore()}>
      <MemoryRouter initialEntries={[path]}>
        <I18nextProvider i18n={i18n}>
          <SidebarDrawerProvider>
            <SidebarShell>
              <nav>navigation</nav>
            </SidebarShell>
            <Header />
          </SidebarDrawerProvider>
        </I18nextProvider>
      </MemoryRouter>
    </Provider>
  )
}

describe('AUDIT-019 — responsive sidebar drawer', () => {
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('hides the sidebar off-canvas on mobile and keeps it static from md up', () => {
    renderLayout()
    const aside = screen.getByTestId('sidebar')
    // Off-canvas by default: this is what stopped `main` from being squeezed to 134 px.
    expect(aside.className).toContain('-translate-x-full')
    expect(aside.className).toContain('fixed')
    // The desktop column is preserved unchanged.
    expect(aside.className).toContain('md:static')
    expect(aside.className).toContain('md:translate-x-0')
  })

  it('exposes a hamburger button that opens the drawer', () => {
    renderLayout()
    const button = screen.getByRole('button', { name: 'Ouvrir le menu' })
    expect(button.className).toContain('md:hidden')
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveAttribute('aria-controls', 'app-sidebar')

    fireEvent.click(button)

    const aside = screen.getByTestId('sidebar')
    expect(aside.className).toContain('translate-x-0')
    expect(aside.className).not.toContain('-translate-x-full')
    expect(button).toHaveAttribute('aria-expanded', 'true')
  })

  it('closes the drawer from the scrim and from the close button', () => {
    renderLayout()
    const button = screen.getByRole('button', { name: 'Ouvrir le menu' })

    fireEvent.click(button)
    fireEvent.click(screen.getByTestId('sidebar-scrim'))
    expect(screen.getByTestId('sidebar').className).toContain('-translate-x-full')

    fireEvent.click(button)
    fireEvent.click(screen.getByRole('button', { name: 'Fermer le menu' }))
    expect(screen.getByTestId('sidebar').className).toContain('-translate-x-full')
  })

  it('closes the drawer when Escape is pressed', () => {
    renderLayout()
    fireEvent.click(screen.getByRole('button', { name: 'Ouvrir le menu' }))
    expect(screen.getByTestId('sidebar').className).toContain('translate-x-0')

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.getByTestId('sidebar').className).toContain('-translate-x-full')
  })

  it('keeps the scrim hidden while the drawer is closed', () => {
    renderLayout()
    expect(screen.getByTestId('sidebar-scrim').className).toContain('hidden')
    fireEvent.click(screen.getByRole('button', { name: 'Ouvrir le menu' }))
    expect(screen.getByTestId('sidebar-scrim').className).toContain('block')
  })
})
