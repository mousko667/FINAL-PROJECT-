import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Download, Loader2, ChevronDown } from 'lucide-react'

interface ExportMenuProps {
  /** Endpoint that accepts ?format=csv|excel|pdf and returns a file blob. */
  endpoint: string
  /** Base filename (without extension). */
  filename: string
  /** Extra query params (e.g. current filters) merged into the request. */
  params?: Record<string, string | number | undefined>
  label?: string
}

const FORMATS: { key: string; ext: string; mime: string; label: string }[] = [
  { key: 'csv', ext: 'csv', mime: 'text/csv', label: 'CSV' },
  { key: 'excel', ext: 'xlsx', mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', label: 'Excel' },
  { key: 'pdf', ext: 'pdf', mime: 'application/pdf', label: 'PDF' },
]

/**
 * Unified export control: one button → CSV / Excel / PDF, all via authenticated blob download.
 * Used across the app so every list offers the same formats (Module 11 / requirement: "tous les exports").
 */
export function ExportMenu({ endpoint, filename, params, label }: ExportMenuProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)

  const download = async (format: string, ext: string, mime: string) => {
    setBusy(format)
    try {
      const res = await apiClient.get(endpoint, {
        params: { ...params, format },
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: mime }))
      const a = document.createElement('a')
      a.href = url
      a.download = `${filename}.${ext}`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } finally {
      setBusy(null)
      setOpen(false)
    }
  }

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 border px-3 py-2 rounded-lg hover:bg-gray-50 text-sm font-medium text-gray-700"
      >
        {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
        {label ?? t('app.export', 'Exporter')}
        <ChevronDown className="w-3.5 h-3.5" />
      </button>
      {open && (
        <div className="absolute right-0 mt-1 w-36 bg-white border rounded-lg shadow-lg z-20 py-1">
          {FORMATS.map(f => (
            <button
              key={f.key}
              type="button"
              disabled={busy !== null}
              onClick={() => download(f.key, f.ext, f.mime)}
              className="w-full text-left px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-50"
            >
              {f.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
