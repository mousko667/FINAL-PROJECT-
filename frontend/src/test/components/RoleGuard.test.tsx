import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { RoleGuard } from '@/components/auth/RoleGuard'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

const makeStore = (user: AuthUser | null = null) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: {
        user,
        accessToken: user ? 'token' : null,
        refreshToken: null,
        isAuthenticated: !!user,
      },
    },
  })

const adminUser: AuthUser = {
  id: '1',
  username: 'admin',
  email: 'admin@oct.fr',
  roles: ['ROLE_ADMIN'],
}

const dafUser: AuthUser = {
  id: '2',
  username: 'daf',
  email: 'daf@oct.fr',
  roles: ['ROLE_DAF'],
}

function renderWithStore(ui: React.ReactElement, store = makeStore()) {
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
      </MemoryRouter>
    </Provider>
  )
}

describe('RoleGuard', () => {
  it('renders children when user has required role', () => {
    renderWithStore(
      <RoleGuard allowedRoles={['ROLE_ADMIN']}>
        <span>admin content</span>
      </RoleGuard>,
      makeStore(adminUser)
    )
    expect(screen.getByText('admin content')).toBeDefined()
  })

  it('hides children when user lacks required role', () => {
    renderWithStore(
      <RoleGuard allowedRoles={['ROLE_ADMIN']}>
        <span>admin content</span>
      </RoleGuard>,
      makeStore(dafUser)
    )
    expect(screen.queryByText('admin content')).toBeNull()
  })

  it('renders fallback when user lacks required role', () => {
    renderWithStore(
      <RoleGuard allowedRoles={['ROLE_ADMIN']} fallback={<span>no access</span>}>
        <span>admin content</span>
      </RoleGuard>,
      makeStore(dafUser)
    )
    expect(screen.getByText('no access')).toBeDefined()
    expect(screen.queryByText('admin content')).toBeNull()
  })

  it('renders fallback when no user is logged in', () => {
    renderWithStore(
      <RoleGuard allowedRoles={['ROLE_ADMIN']} fallback={<span>not logged in</span>}>
        <span>secret</span>
      </RoleGuard>,
      makeStore(null)
    )
    expect(screen.getByText('not logged in')).toBeDefined()
  })

  it('allows users with multiple roles when at least one matches', () => {
    const multiUser: AuthUser = { ...adminUser, roles: ['ROLE_DAF', 'ROLE_ADMIN'] }
    renderWithStore(
      <RoleGuard allowedRoles={['ROLE_DAF']}>
        <span>daf content</span>
      </RoleGuard>,
      makeStore(multiUser)
    )
    expect(screen.getByText('daf content')).toBeDefined()
  })
})
