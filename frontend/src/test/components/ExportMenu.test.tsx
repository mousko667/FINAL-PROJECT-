import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { ExportMenu } from '@/components/ui/ExportMenu'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderMenu() {
  return render(
    <I18nextProvider i18n={i18n}>
      <ExportMenu endpoint="/invoices/export" filename="invoices" />
    </I18nextProvider>
  )
}

async function openMenuAndClickCsv() {
  fireEvent.click(screen.getByRole('button', { name: /exporter/i }))
  fireEvent.click(await screen.findByText('CSV'))
}

describe('ExportMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom does not implement these; stub them for the download path.
    window.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    window.URL.revokeObjectURL = vi.fn()
  })

  it('does not download anything and shows an error message when the request fails', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('Network Error'))
    const createElementSpy = vi.spyOn(document, 'createElement')

    renderMenu()
    await openMenuAndClickCsv()

    await waitFor(() => expect(apiClient.get).toHaveBeenCalled())

    // No anchor should be created/clicked, and no blob URL created, on failure.
    expect(createElementSpy).not.toHaveBeenCalledWith('a')
    expect(window.URL.createObjectURL).not.toHaveBeenCalled()

    // The error message must be visible to the user.
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent("Échec de l'export. Veuillez réessayer.")
    })

    createElementSpy.mockRestore()
  })

  it('downloads the file and shows no error when the request succeeds', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: new Blob(['a,b,c']) } as never)
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderMenu()
    await openMenuAndClickCsv()

    await waitFor(() => expect(window.URL.createObjectURL).toHaveBeenCalled())
    expect(clickSpy).toHaveBeenCalled()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    clickSpy.mockRestore()
  })
})
