export type InvoiceStatus =
  | 'BROUILLON'
  | 'SOUMIS'
  | 'EN_VALIDATION_N1'
  | 'EN_VALIDATION_N2'
  | 'VALIDE'
  | 'BON_A_PAYER'
  | 'PAYE'
  | 'ARCHIVE'
  | 'REJETE'

export interface InvoiceLineItem {
  id?: string
  description: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface InvoiceDocument {
  id: string
  fileName: string
  fileType: string
  fileSize: number
  uploadedAt: string
  downloadUrl?: string
}

export interface Invoice {
  id: string
  referenceNumber: string
  supplierId?: string
  supplierName: string
  supplierEmail?: string
  supplierTaxId?: string
  purchaseOrderId?: string
  amount: number
  currency: string
  issueDate: string
  dueDate: string
  status: InvoiceStatus
  dataSensitivity?: 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL'
  matchingStatus?: 'MATCHED' | 'PARTIAL' | 'MISMATCH' | 'OVERRIDDEN' | null
  description?: string
  department?: {
    id: string
    name: string
    nameEn?: string
    nameFr?: string
    code: string
  }
  lineItems?: InvoiceLineItem[]
  documents?: InvoiceDocument[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  pageNumber: number
  pageSize: number
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}
