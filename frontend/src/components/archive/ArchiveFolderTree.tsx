import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { ArchiveFolder, ArchiveFolderCreateRequest, ArchiveFolderUpdateRequest } from '@/types/archive'
import apiClient from '@/services/apiClient'
import { Folder, FolderOpen, MoreVertical, Plus, Edit2, Trash2, ChevronRight, ChevronDown, Loader2 } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

interface ArchiveFolderTreeProps {
  selectedFolderId: string | null
  onSelectFolder: (id: string | null) => void
}

export default function ArchiveFolderTree({ selectedFolderId, onSelectFolder }: ArchiveFolderTreeProps) {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ROLE_ADMIN')
  const queryClient = useQueryClient()

  const [expandedFolders, setExpandedFolders] = useState<Record<string, boolean>>({})
  const [isCreating, setIsCreating] = useState(false)
  const [editingFolderId, setEditingFolderId] = useState<string | null>(null)
  const [formData, setFormData] = useState({ name: '', description: '', parentId: '' })

  const { data: folders = [], isLoading } = useQuery({
    queryKey: ['archive-folders'],
    queryFn: async () => {
      const res = await apiClient.get<{ data: ArchiveFolder[] }>('/archive/folders')
      return res.data.data
    }
  })

  const createMutation = useMutation({
    mutationFn: (data: ArchiveFolderCreateRequest) => apiClient.post('/archive/folders', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['archive-folders'] })
      setIsCreating(false)
      setFormData({ name: '', description: '', parentId: '' })
    }
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ArchiveFolderUpdateRequest }) => apiClient.put(`/archive/folders/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['archive-folders'] })
      setEditingFolderId(null)
    }
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/archive/folders/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['archive-folders'] })
      queryClient.invalidateQueries({ queryKey: ['archive'] }) // Refresh invoices
    }
  })

  const toggleExpand = (id: string, e: React.MouseEvent) => {
    e.stopPropagation()
    setExpandedFolders(prev => ({ ...prev, [id]: !prev[id] }))
  }

  const handleDelete = (id: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (window.confirm(t('archiveFolders.deleteConfirm'))) {
      deleteMutation.mutate(id)
    }
  }

  const startEdit = (folder: ArchiveFolder, e: React.MouseEvent) => {
    e.stopPropagation()
    setFormData({ name: folder.name, description: folder.description || '', parentId: folder.parentId || '' })
    setEditingFolderId(folder.id)
  }

  const saveEdit = () => {
    if (!formData.name.trim()) return
    updateMutation.mutate({ id: editingFolderId!, data: formData })
  }

  const saveCreate = () => {
    if (!formData.name.trim()) return
    createMutation.mutate(formData)
  }

  const renderFolderForm = (isEdit: boolean, parentId?: string) => (
    <div className="pl-6 pr-2 py-2 border-l-2 border-primary ml-2 my-1 bg-ground rounded-r-lg">
      <input
        autoFocus
        type="text"
        className="w-full text-sm border rounded px-2 py-1 mb-2 outline-none focus:ring-1 focus:ring-primary"
        placeholder={t('archiveFolders.name')}
        value={formData.name}
        onChange={e => setFormData({ ...formData, name: e.target.value })}
        onKeyDown={e => {
          if (e.key === 'Enter') isEdit ? saveEdit() : saveCreate()
          if (e.key === 'Escape') {
            isEdit ? setEditingFolderId(null) : setIsCreating(false)
          }
        }}
      />
      <div className="flex gap-2 justify-end">
        <button
          onClick={() => isEdit ? setEditingFolderId(null) : setIsCreating(false)}
          className="text-xs text-ink-faint hover:text-ink-soft"
        >
          {t('app.cancel', 'Annuler')}
        </button>
        <button
          onClick={() => isEdit ? saveEdit() : saveCreate()}
          disabled={!formData.name.trim() || createMutation.isPending || updateMutation.isPending}
          className="text-xs bg-primary text-white px-2 py-1 rounded hover:bg-primary/90 disabled:opacity-50"
        >
          {t('app.save', 'Enregistrer')}
        </button>
      </div>
    </div>
  )

  const renderTree = (parentId: string | null = null, depth = 0) => {
    const children = folders.filter(f => (f.parentId || null) === parentId)
    if (children.length === 0) return null

    return (
      <ul className="space-y-0.5">
        {children.map(folder => {
          const isSelected = selectedFolderId === folder.id
          const hasChildren = folders.some(f => f.parentId === folder.id)
          const isExpanded = expandedFolders[folder.id]
          const isEditing = editingFolderId === folder.id

          return (
            <li key={folder.id}>
              {isEditing ? (
                renderFolderForm(true)
              ) : (
                <div
                  onClick={() => onSelectFolder(folder.id)}
                  className={`group flex items-center justify-between py-1.5 px-2 rounded-lg cursor-pointer transition-colors ${isSelected ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-ground text-ink-soft'}`}
                  style={{ paddingLeft: `${depth * 12 + 8}px` }}
                >
                  <div className="flex items-center gap-1.5 flex-1 min-w-0">
                    {hasChildren ? (
                      <button onClick={e => toggleExpand(folder.id, e)} className="p-0.5 hover:bg-surface-hover rounded text-ink-faint">
                        {isExpanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                      </button>
                    ) : (
                      <div className="w-4.5" /> // spacer
                    )}
                    {isSelected ? <FolderOpen className="w-4 h-4 shrink-0" /> : <Folder className="w-4 h-4 shrink-0" />}
                    <span className="truncate text-sm">{folder.name}</span>
                    <span className="text-[10px] bg-surface border-hairline px-1.5 py-0.5 rounded-full text-ink-faint ml-1">
                      {folder.invoiceCount}
                    </span>
                  </div>

                  {isAdmin && (
                    <div className="hidden group-hover:flex items-center gap-1">
                      {depth === 0 && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            setFormData({ name: '', description: '', parentId: folder.id })
                            setIsCreating(true)
                          }}
                          className="p-1 text-ink-faint hover:text-primary hover:bg-primary/10 rounded"
                          title={t('archiveFolders.new')}
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      )}
                      <button
                        onClick={(e) => startEdit(folder, e)}
                        className="p-1 text-ink-faint hover:text-primary hover:bg-primary/10 rounded"
                      >
                        <Edit2 className="w-3 h-3" />
                      </button>
                      <button
                        onClick={(e) => handleDelete(folder.id, e)}
                        disabled={deleteMutation.isPending}
                        className="p-1 text-ink-faint hover:text-red-500 hover:bg-crit/10 rounded"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  )}
                </div>
              )}
              
              {/* Nested children */}
              {isExpanded && renderTree(folder.id, depth + 1)}
            </li>
          )
        })}
      </ul>
    )
  }

  if (isLoading) {
    return <div className="flex justify-center p-4"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
  }

  return (
    <div className="w-64 shrink-0 border-r bg-ground/50 flex flex-col h-full min-h-[500px]">
      <div className="p-4 border-b flex items-center justify-between bg-surface">
        <h2 className="font-semibold text-ink">{t('archiveFolders.title')}</h2>
        {isAdmin && !isCreating && (
          <button
            onClick={() => {
              setFormData({ name: '', description: '', parentId: '' })
              setIsCreating(true)
            }}
            className="p-1.5 text-primary hover:bg-primary/10 rounded-lg transition-colors"
            title={t('archiveFolders.new')}
          >
            <Plus className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="p-2 flex-1 overflow-y-auto">
        {/* Special fixed folders */}
        <div className="space-y-0.5 mb-4">
          <button
            onClick={() => onSelectFolder(null)}
            className={`w-full flex items-center gap-2 py-1.5 px-2 rounded-lg text-sm transition-colors ${selectedFolderId === null ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-ground text-ink-soft'}`}
          >
            <ArchiveFolderIcon />
            {t('archiveFolders.all')}
          </button>
          <button
            onClick={() => onSelectFolder('NONE')}
            className={`w-full flex items-center gap-2 py-1.5 px-2 rounded-lg text-sm transition-colors ${selectedFolderId === 'NONE' ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-ground text-ink-soft'}`}
          >
            <ArchiveFolderIcon unclassified />
            {t('archiveFolders.unclassified')}
          </button>
        </div>

        {/* Create root form */}
        {isCreating && !formData.parentId && renderFolderForm(false)}

        {/* Folder Tree */}
        {renderTree(null, 0)}
      </div>
    </div>
  )
}

function ArchiveFolderIcon({ unclassified }: { unclassified?: boolean }) {
  return (
    <svg className={`w-4 h-4 ${unclassified ? 'text-ink-faint' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
    </svg>
  )
}
