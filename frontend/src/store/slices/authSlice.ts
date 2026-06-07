import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/store'

export type UserRole = string

export interface AuthUser {
  id: string
  username: string
  email?: string
  roles: UserRole[]
  departmentId?: string
  supplierId?: string
}

interface AuthState {
  user: AuthUser | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
}

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('user')
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

const initialState: AuthState = {
  user: loadUser(),
  accessToken: localStorage.getItem('accessToken'),
  refreshToken: localStorage.getItem('refreshToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{
        user: AuthUser
        accessToken: string
        refreshToken: string
      }>
    ) => {
      state.user = action.payload.user
      state.accessToken = action.payload.accessToken
      state.refreshToken = action.payload.refreshToken
      state.isAuthenticated = true
      localStorage.setItem('accessToken', action.payload.accessToken)
      localStorage.setItem('refreshToken', action.payload.refreshToken)
      localStorage.setItem('user', JSON.stringify(action.payload.user))
    },
    updateToken: (state, action: PayloadAction<string>) => {
      state.accessToken = action.payload
      localStorage.setItem('accessToken', action.payload)
    },
    logout: (state) => {
      state.user = null
      state.accessToken = null
      state.refreshToken = null
      state.isAuthenticated = false
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
    },
  },
})

export const { setCredentials, updateToken, logout } = authSlice.actions
export default authSlice.reducer

// Selectors
export const selectIsSupplier = (state: RootState) =>
  state.auth.user?.roles.includes('ROLE_SUPPLIER') ?? false

export const selectSupplierId = (state: RootState) =>
  state.auth.user?.supplierId ?? null
