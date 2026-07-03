export interface ArchiveFolder {
  id: string
  name: string
  description?: string
  parentId?: string
  parentName?: string
  invoiceCount: number
  createdAt: string
}

export interface ArchiveFolderCreateRequest {
  name: string
  description?: string
  parentId?: string
}

export interface ArchiveFolderUpdateRequest {
  name: string
  description?: string
  parentId?: string
}
