import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Trash2, Upload, FileText, Image, FileSpreadsheet } from 'lucide-react'
import { cn } from '@/lib/utils'

interface DocumentUploaderProps {
  onFilesChange: (files: File[]) => void
  existingFiles?: File[]
  maxSizeMb?: number
  accept?: string
  disabled?: boolean
}

const MIME_ICONS: Record<string, React.ReactNode> = {
  'application/pdf': <FileText className="w-5 h-5 text-red-500" />,
  'image/png': <Image className="w-5 h-5 text-blue-500" />,
  'image/jpeg': <Image className="w-5 h-5 text-blue-500" />,
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': <FileSpreadsheet className="w-5 h-5 text-green-600" />,
  'application/vnd.ms-excel': <FileSpreadsheet className="w-5 h-5 text-green-600" />,
}

export function DocumentUploader({
  onFilesChange,
  existingFiles = [],
  maxSizeMb = 10,
  accept = '.pdf,.png,.jpg,.jpeg,.xlsx,.xls',
  disabled = false,
}: DocumentUploaderProps) {
  const { t } = useTranslation()
  const [files, setFiles] = useState<File[]>(existingFiles)
  const [dragOver, setDragOver] = useState(false)
  const [errors, setErrors] = useState<string[]>([])

  const addFiles = useCallback(
    (newFiles: File[]) => {
      const maxBytes = maxSizeMb * 1024 * 1024
      const errs: string[] = []
      const valid: File[] = []

      for (const f of newFiles) {
        if (f.size > maxBytes) {
          errs.push(`${f.name}: dépasse ${maxSizeMb} Mo`)
        } else {
          valid.push(f)
        }
      }

      if (errs.length) setErrors(errs)
      const updated = [...files, ...valid]
      setFiles(updated)
      onFilesChange(updated)
    },
    [files, maxSizeMb, onFilesChange]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragOver(false)
      if (disabled) return
      addFiles(Array.from(e.dataTransfer.files))
    },
    [addFiles, disabled]
  )

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) addFiles(Array.from(e.target.files))
    e.target.value = ''
  }

  const removeFile = (idx: number) => {
    const updated = files.filter((_, i) => i !== idx)
    setFiles(updated)
    onFilesChange(updated)
  }

  const getIcon = (file: File) =>
    MIME_ICONS[file.type] ?? <FileText className="w-5 h-5 text-gray-400" />

  return (
    <div className="space-y-3">
      {/* Drop zone */}
      <label
        id="document-uploader"
        htmlFor="doc-upload-input"
        onDragOver={(e) => { e.preventDefault(); if (!disabled) setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={cn(
          'flex flex-col items-center justify-center w-full h-44 border-2 border-dashed rounded-xl cursor-pointer transition-colors',
          dragOver ? 'border-primary bg-primary/5' : 'border-gray-300 hover:bg-gray-50',
          disabled && 'opacity-50 cursor-not-allowed'
        )}
      >
        <Upload className={cn('w-10 h-10 mb-2', dragOver ? 'text-primary' : 'text-gray-300')} />
        <p className="text-sm font-medium text-gray-600">
          {dragOver ? 'Déposez ici…' : 'Glissez-déposez ou cliquez pour ajouter'}
        </p>
        <p className="text-xs text-muted-foreground mt-1">
          PDF, PNG, JPG, XLSX (max {maxSizeMb} Mo par fichier)
        </p>
        <input
          id="doc-upload-input"
          type="file"
          multiple
          accept={accept}
          disabled={disabled}
          className="hidden"
          onChange={handleInput}
        />
      </label>

      {/* Validation errors */}
      {errors.length > 0 && (
        <ul className="space-y-1">
          {errors.map((e, i) => (
            <li key={i} className="text-xs text-red-600 bg-red-50 px-3 py-1.5 rounded-lg">
              {e}
            </li>
          ))}
        </ul>
      )}

      {/* File list */}
      {files.length > 0 && (
        <ul className="space-y-2">
          {files.map((f, i) => (
            <li
              key={i}
              id={`doc-item-${i}`}
              className="flex items-center gap-3 px-3 py-2.5 bg-gray-50 border rounded-xl"
            >
              {getIcon(f)}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-700 truncate">{f.name}</p>
                <p className="text-xs text-muted-foreground">{(f.size / 1024).toFixed(1)} KB</p>
              </div>
              {/* Progress bar placeholder — in real use, track upload progress via axios onUploadProgress */}
              <div className="w-16 h-1.5 bg-green-100 rounded-full overflow-hidden">
                <div className="h-full w-full bg-green-500 rounded-full" />
              </div>
              {!disabled && (
                <button
                  id={`btn-remove-doc-${i}`}
                  type="button"
                  onClick={() => removeFile(i)}
                  className="p-1 text-gray-400 hover:text-red-500 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
