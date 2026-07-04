import { describe, it, expect, afterEach, vi } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import InvoiceCreatePage from '@/pages/InvoiceCreatePage'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => vi.fn() }
})

// Departments come back with distinct FR/EN names, mirroring the real V1 seed
// (e.g. "Informatique" vs "Information Technology") so the test can prove which
// one the UI actually renders for the active language (RT-5).
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/suppliers') {
        return Promise.resolve({ data: { data: { content: [] } } })
      }
      if (url === '/departments') {
        return Promise.resolve({
          data: {
            data: {
              content: [
                { id: 'd1', code: 'INFO', nameFr: 'Informatique', nameEn: 'Information Technology' },
              ],
            },
          },
        })
      }
      return Promise.resolve({ data: { data: { content: [] } } })
    }),
  },
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>
          <InvoiceCreatePage />
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('InvoiceCreatePage department name locale (RT-5)', () => {
  afterEach(() => {
    cleanup()
    i18n.changeLanguage('fr')
  })

  it('shows the French department name when i18n.language is fr (default)', async () => {
    await i18n.changeLanguage('fr')
    renderPage()
    expect(await screen.findByRole('option', { name: /Informatique \(INFO\)/ })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Information Technology/ })).toBeNull()
  })

  it('shows the English department name when i18n.language is en', async () => {
    await i18n.changeLanguage('en')
    renderPage()
    expect(await screen.findByRole('option', { name: /Information Technology \(INFO\)/ })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /^Informatique/ })).toBeNull()
  })
})
