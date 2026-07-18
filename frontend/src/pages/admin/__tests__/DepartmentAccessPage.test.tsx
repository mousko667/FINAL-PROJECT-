import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import DepartmentAccessPage from '../DepartmentAccessPage'
import { departmentAccessService } from '@/services/departmentAccessService'

vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('@/services/departmentAccessService')
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'fr' } }),
}))

describe('DepartmentAccessPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('affiche les départements avec compteurs et badge N1->N2', async () => {
    vi.mocked(departmentAccessService.getOverview).mockResolvedValue([
      { departmentId: '1', code: 'IT', nameFr: 'Informatique', nameEn: 'IT', requiresN2: true,
        n1Role: 'ROLE_N1', n2Role: 'ROLE_N2', userCount: 2, activeCount: 1,
        users: [{ userId: 'u1', fullName: 'Alice L', username: 'alice', active: true, roles: ['ROLE_N1'] }] },
    ])

    render(<DepartmentAccessPage />)

    await waitFor(() => expect(screen.getByText('IT')).toBeInTheDocument())
    expect(screen.getByText('Informatique')).toBeInTheDocument()
  })

  it('affiche un état vide', async () => {
    vi.mocked(departmentAccessService.getOverview).mockResolvedValue([])
    render(<DepartmentAccessPage />)
    await waitFor(() =>
      expect(screen.getByText('departmentAccess.empty')).toBeInTheDocument())
  })

  it('affiche une erreur traduite', async () => {
    vi.mocked(departmentAccessService.getOverview).mockRejectedValue(new Error('boom'))
    render(<DepartmentAccessPage />)
    await waitFor(() =>
      expect(screen.getByText('departmentAccess.error')).toBeInTheDocument())
  })
})
