import { Document, Page } from './pdfWorker'

export interface PdfDocumentProps {
  url: string
  zoom: number
  rotation: number
  pageNumber: number
  onLoadSuccess: (numPages: number) => void
  onLoadError: () => void
}

export function PdfDocument({ url, zoom, rotation, pageNumber, onLoadSuccess, onLoadError }: PdfDocumentProps) {
  return (
    <Document
      file={url}
      onLoadSuccess={(pdf: { numPages: number }) => onLoadSuccess(pdf.numPages)}
      onLoadError={onLoadError}
      loading={<div className="text-sm text-ink-faint p-8">…</div>}
    >
      <Page pageNumber={pageNumber} scale={zoom} rotate={rotation} />
    </Document>
  )
}
