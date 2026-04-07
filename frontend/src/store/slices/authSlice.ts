import { createSlice, PayloadAction } from '@reduxjs/toolkit'

export type UserRole =
  | 'ROLE_ADMIN'
  | 'ROLE_ASSISTANT_COMPTABLE'
  | 'ROLE_VALIDATEUR_N1'
  | 'ROLE_VALIDATEUR_N2'
  | 'ROLE_DAF'

export interface AuthUser {
  id: string
  username: string
  email: string
  roles: UserRole[]
  departmentId?: string
}

interface AuthState {
  user: AuthUser | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
}

const initialState: AuthState = {
  user: null,
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
    },
  },
})

export const { setCredentials, updateToken, logout } = authSlice.actions
export default authSlice.reducer
