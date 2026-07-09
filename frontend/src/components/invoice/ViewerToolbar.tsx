import { useTranslation } from 'react-i18next'
import { ZoomIn, ZoomOut, RotateCw, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react'

export interface ViewerToolbarProps {
  zoom: number
  rotation: number
  canZoomIn: boolean
  canZoomOut: boolean
  pageNumber?: number
  numPages?: number
  onZoomIn: () => void
  onZoomOut: () => void
  onRotate: () => void
  onReset: () => void
  onPrevPage?: () => void
  onNextPage?: () => void
}

export function ViewerToolbar(props: ViewerToolbarProps) {
  const { t } = useTranslation()
  const { zoom, canZoomIn, canZoomOut, pageNumber, numPages } = props
  const multiPage = (numPages ?? 0) > 1
  const btn = 'p-1.5 rounded text-ink-soft hover:bg-ground disabled:opacity-40 disabled:cursor-not-allowed'

  return (
    <div className="flex items-center gap-1 px-3 py-2 border-b bg-ground">
      <button type="button" className={btn} aria-label={t('invoice.viewer.zoomOut', 'Zoom arrière')}
        onClick={props.onZoomOut} disabled={!canZoomOut}><ZoomOut className="w-4 h-4" /></button>
      <span className="text-xs tabular-nums w-12 text-center">{Math.round(zoom * 100)}%</span>
      <button type="button" className={btn} aria-label={t('invoice.viewer.zoomIn', 'Zoom avant')}
        onClick={props.onZoomIn} disabled={!canZoomIn}><ZoomIn className="w-4 h-4" /></button>
      <button type="button" className={btn} aria-label={t('invoice.viewer.rotate', 'Pivoter')}
        onClick={props.onRotate}><RotateCw className="w-4 h-4" /></button>
      <button type="button" className={btn} aria-label={t('invoice.viewer.resetView', 'Réinitialiser')}
        onClick={props.onReset}><RefreshCw className="w-4 h-4" /></button>

      {multiPage && (
        <div className="flex items-center gap-1 ml-auto text-xs text-ink-soft">
          <button type="button" className={btn} aria-label={t('invoice.viewer.prevPage', 'Page précédente')}
            onClick={props.onPrevPage} disabled={(pageNumber ?? 1) <= 1}><ChevronLeft className="w-4 h-4" /></button>
          <span>{t('invoice.viewer.page', 'Page')} {pageNumber} {t('invoice.viewer.of', 'sur')} {numPages}</span>
          <button type="button" className={btn} aria-label={t('invoice.viewer.nextPage', 'Page suivante')}
            onClick={props.onNextPage} disabled={(pageNumber ?? 1) >= (numPages ?? 1)}><ChevronRight className="w-4 h-4" /></button>
        </div>
      )}
    </div>
  )
}
