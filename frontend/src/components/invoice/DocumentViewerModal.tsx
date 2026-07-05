import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Panel } from "@/components/ui/Panel"
import {  Download  } from 'lucide-react'
import { ViewerToolbar } from './ViewerToolbar'
import { PdfDocument } from './PdfDocument'

interface DocumentViewerModalProps {
  url: string
  filename: string
  fileType?: string
  onClose: () => void
}

const ZOOM_MIN = 0.5
const ZOOM_MAX = 3.0
const ZOOM_STEP = 0.25

/**
 * M9 — visualiseur de documents in-app avec contrôles zoom/rotation/reset.
 * PDF rendus via react-pdf (scale + rotate), images via transform CSS.
 * Les types non prévisualisables retombent sur un lien de téléchargement.
 */
export function DocumentViewerModal({ url, filename, fileType, onClose }: DocumentViewerModalProps) {
  const { t } = useTranslation()
  const isPdf = (fileType ?? '').includes('pdf') || filename.toLowerCase().endsWith('.pdf')
  const isImage = (fileType ?? '').startsWith('image/')
    || /\.(png|jpe?g|gif|tiff?|webp)$/i.test(filename)

  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [pageNumber, setPageNumber] = useState(1)
  const [numPages, setNumPages] = useState(0)
  const [pdfError, setPdfError] = useState(false)

  const zoomIn = () => setZoom(z => Math.min(ZOOM_MAX, +(z + ZOOM_STEP).toFixed(2)))
  const zoomOut = () => setZoom(z => Math.max(ZOOM_MIN, +(z - ZOOM_STEP).toFixed(2)))
  const rotate = () => setRotation(r => (r + 90) % 360)
  const reset = () => { setZoom(1); setRotation(0) }

  const previewable = isPdf || isImage

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-surface rounded-2xl shadow-2xl w-full max-w-4xl h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <h2 className="text-sm font-semibold text-ink truncate">{filename}</h2>
          <div className="flex items-center gap-3">
            <a href={url} download className="text-ink-faint hover:text-primary" title={t('app.download', 'Télécharger')}>
              <Download className="w-4 h-4" />
            </a>
            <button onClick={onClose} className="text-ink-faint hover:text-ink-soft text-xl leading-none">×</button>
          </div>
        </div>

        {previewable && !pdfError && (
          <ViewerToolbar
            zoom={zoom}
            rotation={rotation}
            canZoomIn={zoom < ZOOM_MAX}
            canZoomOut={zoom > ZOOM_MIN}
            pageNumber={isPdf ? pageNumber : undefined}
            numPages={isPdf ? numPages : undefined}
            onZoomIn={zoomIn}
            onZoomOut={zoomOut}
            onRotate={rotate}
            onReset={reset}
            onPrevPage={() => setPageNumber(p => Math.max(1, p - 1))}
            onNextPage={() => setPageNumber(p => Math.min(numPages, p + 1))}
          />
        )}

        <div className="flex-1 overflow-auto bg-ground flex items-center justify-center">
          {isPdf && !pdfError ? (
            <PdfDocument
              url={url}
              zoom={zoom}
              rotation={rotation}
              pageNumber={pageNumber}
              onLoadSuccess={setNumPages}
              onLoadError={() => setPdfError(true)}
            />
          ) : isImage && !pdfError ? (
            <img
              src={url}
              alt={filename}
              className="max-w-full max-h-full object-contain"
              style={{ transform: `scale(${zoom}) rotate(${rotation}deg)` }}
              onError={() => setPdfError(true)}
            />
          ) : (
            <div className="text-center text-sm text-ink-faint p-8">
              <p>{pdfError ? t('invoice.viewer.loadError', 'Impossible de charger le document.') : t('invoice.viewer.noPreview', 'Aperçu non disponible pour ce type de fichier.')}</p>
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
