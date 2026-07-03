import apiClient from '@/services/apiClient'

export interface BackupStatusResponse {
  lastBackupAt: string | null
  status: 'OK' | 'FAILED' | 'UNKNOWN'
  detail: string | null
}

export async function listBackups() {
  const { data } = await apiClient.get('/backups')
  return data.data as string[]
}

export async function createBackup() {
  const { data } = await apiClient.post('/backups')
  return data.data as BackupStatusResponse
}

export async function restoreBackup(filename: string) {
  const { data } = await apiClient.post(`/backups/${filename}/restore`)
  return data.data as BackupStatusResponse
}

export async function getBackupStatus() {
  const { data } = await apiClient.get('/compliance/backup-status') // Existing endpoint
  return data.data as BackupStatusResponse
}

export interface BackupAuditLogResponse {
  id: string
  operation: string
  filename: string | null
  status: string
  errorMessage: string | null
  triggeredBy: string
  createdAt: string
}

export async function getAuditLogs() {
  const { data } = await apiClient.get('/backups/audit-logs')
  return data.data as BackupAuditLogResponse[]
}
