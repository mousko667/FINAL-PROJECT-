import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'

interface Props {
  isOpen: boolean
  onClose: () => void
  onResolve: (reason: string) => Promise<void>
  poLineId: string
  description: string
}

export default function MatchingLineResolveModal({ isOpen, onClose, onResolve, poLineId, description }: Props) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (reason.length < 5) {
      setError(t('matching.resolve.errorLength'))
      return
    }
    setLoading(true)
    try {
      await onResolve(reason)
      setReason('')
      onClose()
    } catch (err: any) {
      setError(err.response?.data?.message || t('matching.resolve.errorGeneric'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-md p-6">
        <h2 className="text-lg font-semibold mb-4">{t('matching.resolve.title')}</h2>
        <p className="text-sm text-gray-600 mb-4">
          {t('matching.resolve.description', { item: description })}
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">
              {t('matching.resolve.reasonLabel')}
            </label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full border rounded p-2 text-sm"
              rows={3}
              placeholder={t('matching.resolve.reasonPlaceholder')}
              required
              minLength={5}
            />
          </div>

          {error && <p className="text-sm text-red-500">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="px-4 py-2 text-sm border rounded hover:bg-gray-50"
            >
              {t('app.cancel')}
            </button>
            <button
              type="submit"
              disabled={loading || reason.length < 5}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 flex items-center gap-2"
            >
              {loading && <Loader2 className="w-4 h-4 animate-spin" />}
              {t('matching.resolve.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
