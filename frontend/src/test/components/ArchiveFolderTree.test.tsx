import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import ArchiveFolderTree from '@/components/archive/ArchiveFolderTree'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// Mock the Redux store selector — ArchiveFolderTree reads the current user's
// roles via useAppSelector((state) => state.auth.user) to decide admin actions.
vi.mock('@/store/hooks', () => ({
  useAppSelector: (selector: (state: unknown) => unknown) =>
    selector({ auth: { user: { roles: ['ROLE_ADMIN'] } } }),
}))

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

// Mock apiClient
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: { data: [{ id: '1', name: 'Folder 1', invoiceCount: 5, createdAt: '' }] } }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
}))

describe('ArchiveFolderTree', () => {
  it('renders folders and handles selection', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const onSelect = vi.fn()

    render(
      <QueryClientProvider client={queryClient}>
        <ArchiveFolderTree selectedFolderId={null} onSelectFolder={onSelect} />
      </QueryClientProvider>
    )

    // Wait for the folder to be fetched and rendered
    await waitFor(() => {
      expect(screen.getByText('Folder 1')).toBeInTheDocument()
    })

    // Click on the folder
    fireEvent.click(screen.getByText('Folder 1'))
    expect(onSelect).toHaveBeenCalledWith('1')

    // Click on "All invoices"
    fireEvent.click(screen.getByText('archiveFolders.all'))
    expect(onSelect).toHaveBeenCalledWith(null)
  })
})
