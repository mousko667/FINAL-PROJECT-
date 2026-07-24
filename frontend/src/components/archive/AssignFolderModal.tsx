import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ArchiveFolder } from '@/types/archive'
import { X, Folder, Check, Loader2 } from 'lucide-react'
import { notifyApiError } from '@/components/ErrorToaster'

interface AssignFolderModalProps {
  invoiceId: string
  currentFolderId?: string
  onClose: () => void
}

export default function AssignFolderModal({ invoiceId, currentFolderId, onClose }: AssignFolderModalProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(currentFolderId || null)

  const { data: folders = [], isLoading } = useQuery({
    queryKey: ['archive-folders'],
    queryFn: async () => {
      const res = await apiClient.get<{ data: ArchiveFolder[] }>('/archive/folders')
      return res.data.data
    }
  })

  const assignMutation = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.patch(`/archive/invoices/${invoiceId}/folder${selectedFolderId ? `?folderId=${selectedFolderId}` : ''}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['archive'] })
      queryClient.invalidateQueries({ queryKey: ['archive-folders'] })
      onClose()
    }
  })

  const handleSave = () => {
    assignMutation.mutate()
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="bg-surface rounded-[4px] shadow-xl w-full max-w-md flex flex-col max-h-[80vh]">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold text-ink">{t('archiveFolders.assign')}</h2>
          <button onClick={onClose} className="text-ink-faint hover:text-ink-soft transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4 overflow-y-auto flex-1">
          {isLoading ? (
            <div className="flex justify-center p-8"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
          ) : (
            <div className="space-y-1">
              {/* Option to unassign */}
              <button
                onClick={() => setSelectedFolderId(null)}
                className={`w-full flex items-center justify-between p-3 rounded-[4px] border transition-colors ${selectedFolderId === null ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-hairline hover:border-primary/30'}`}
              >
                <div className="flex items-center gap-3 text-sm">
                  <div className="w-8 h-8 rounded-full bg-ground flex items-center justify-center">
                    <Folder className="w-4 h-4 text-ink-soft" />
                  </div>
                  <span className="font-medium text-ink-soft">{t('archiveFolders.unclassified')}</span>
                </div>
                {selectedFolderId === null && <Check className="w-5 h-5 text-primary" />}
              </button>

              {/* Folder list */}
              {folders.map(folder => {
                const isSelected = selectedFolderId === folder.id
                // Identation for child folders
                const isChild = !!folder.parentId
                
                return (
                  <button
                    key={folder.id}
                    onClick={() => setSelectedFolderId(folder.id)}
                    className={`w-full flex items-center justify-between p-3 rounded-[4px] border transition-colors ${isSelected ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-hairline hover:border-primary/30'}`}
                  >
                    <div className="flex items-center gap-3 text-sm" style={{ paddingLeft: isChild ? '2rem' : '0' }}>
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center ${isSelected ? 'bg-primary/20 text-primary' : 'bg-primary/10 text-primary/80'}`}>
                        <Folder className="w-4 h-4" />
                      </div>
                      <span className={`font-medium ${isSelected ? 'text-primary' : 'text-ink'}`}>
                        {folder.name}
                      </span>
                    </div>
                    {isSelected && <Check className="w-5 h-5 text-primary" />}
                  </button>
                )
              })}
            </div>
          )}
        </div>

        <div className="p-4 border-t bg-ground rounded-b-xl flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-ink-soft bg-surface border border-hairline-strong rounded-[4px] hover:bg-ground"
          >
            {t('app.cancel', 'Annuler')}
          </button>
          <button
            onClick={handleSave}
            disabled={assignMutation.isPending}
            className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-[4px] hover:bg-primary/90 disabled:opacity-50 flex items-center gap-2"
          >
            {assignMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('app.save', 'Enregistrer')}
          </button>
        </div>
      </div>
    </div>
  )
}
