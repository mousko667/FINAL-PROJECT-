import { useTranslation } from 'react-i18next'
import { X, Download } from 'lucide-react'

interface DocumentViewerModalProps {
  url: string
  filename: string
  fileType?: string
  onClose: () => void
}

/**
 * M9 — in-app document viewer. Renders PDFs in an iframe and images inline (via the pre-signed
 * MinIO URL); other types fall back to a download prompt. Read-only preview, no extra fetch.
 */
export function DocumentViewerModal({ url, filename, fileType, onClose }: DocumentViewerModalProps) {
  const { t } = useTranslation()
  const isPdf = (fileType ?? '').includes('pdf') || filename.toLowerCase().endsWith('.pdf')
  const isImage = (fileType ?? '').startsWith('image/')
    || /\.(png|jpe?g|gif|tiff?|webp)$/i.test(filename)

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <h2 className="text-sm font-semibold text-gray-800 truncate">{filename}</h2>
          <div className="flex items-center gap-3">
            <a href={url} download className="text-gray-500 hover:text-primary" title={t('app.download', 'Télécharger')}>
              <Download className="w-4 h-4" />
            </a>
            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
          </div>
        </div>
        <div className="flex-1 overflow-auto bg-gray-100 flex items-center justify-center">
          {isPdf ? (
            <iframe src={url} title={filename} className="w-full h-full" />
          ) : isImage ? (
            <img src={url} alt={filename} className="max-w-full max-h-full object-contain" />
          ) : (
            <div className="text-center text-sm text-gray-500 p-8">
              <p>{t('invoice.viewer.noPreview', 'Aperçu non disponible pour ce type de fichier.')}</p>
              <a href={url} download className="inline-flex items-center gap-1.5 mt-3 text-primary hover:underline">
                <Download className="w-4 h-4" /> {t('app.download', 'Télécharger')}
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
