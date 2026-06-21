import apiClient from '@/services/apiClient'
import type { ApiResponse } from '@/types/invoice'

export interface DepartmentAccessUser {
  userId: string
  fullName: string
  username: string
  active: boolean
  roles: string[]
}

export interface DepartmentAccess {
  departmentId: string
  code: string
  nameFr: string
  nameEn: string
  requiresN2: boolean
  n1Role: string | null
  n2Role: string | null
  userCount: number
  activeCount: number
  users: DepartmentAccessUser[]
}

export const departmentAccessService = {
  // apiClient n'ajoute PAS le préfixe /api/v1 (PROB-038).
  getOverview: async (): Promise<DepartmentAccess[]> => {
    const { data } = await apiClient.get<ApiResponse<DepartmentAccess[]>>('/admin/department-access')
    return data.data
  },
}
