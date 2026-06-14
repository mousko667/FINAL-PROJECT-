// Canonical list of assignable staff roles in the system.
// Single source of truth shared by the user form (AdminUserFormPage) and the
// permission matrix (AdminPermissionMatrixPage). Excludes ROLE_SUPPLIER (managed via the
// supplier portal, not assignable here), ROLE_AUDITEUR (removed — V31) and ROLE_VALIDATEUR_N1_FIN.

export interface RoleOption {
  value: string
  /** Full descriptive label (used in the user form dropdown). */
  label: string
  /** Short label for compact contexts such as matrix column headers. */
  short: string
}

export const ROLE_OPTIONS: RoleOption[] = [
  { value: 'ROLE_ADMIN',                label: 'Administrator',                                   short: 'Admin' },
  { value: 'ROLE_DAF',                  label: 'CFO (Directeur Administratif et Financier)',      short: 'DAF' },
  { value: 'ROLE_ASSISTANT_COMPTABLE',  label: 'Accounting Assistant (Assistant comptable)',      short: 'Asst. Compta' },
  { value: 'ROLE_VALIDATEUR_N1_DRH',    label: 'HR Director — N1 (DRH)',                           short: 'N1 DRH' },
  { value: 'ROLE_VALIDATEUR_N1_DG',     label: 'General Manager — N1 (DG)',                        short: 'N1 DG' },
  { value: 'ROLE_VALIDATEUR_N1_INFO',   label: 'IT Manager — N1 (RSI)',                           short: 'N1 INFO' },
  { value: 'ROLE_VALIDATEUR_N2_INFO',   label: 'CIO — N2 (DSI)',                                   short: 'N2 INFO' },
  { value: 'ROLE_VALIDATEUR_N1_TERM',   label: 'Terminal Manager — N1 (DEX)',                      short: 'N1 TERM' },
  { value: 'ROLE_VALIDATEUR_N1_COM',    label: 'Communication Manager — N1 (Resp. Com)',          short: 'N1 COM' },
  { value: 'ROLE_VALIDATEUR_N1_QHSSE',  label: 'QHSSE Manager — N1 (Resp. QHSSE)',                short: 'N1 QHSSE' },
  { value: 'ROLE_VALIDATEUR_N1_INFRA',  label: 'Infrastructure Manager — N1 (Resp. INFRA)',       short: 'N1 INFRA' },
  { value: 'ROLE_VALIDATEUR_N2_INFRA',  label: 'Infrastructure Director — N2 (Directeur INFRA)',  short: 'N2 INFRA' },
  { value: 'ROLE_VALIDATEUR_N1_TECH',   label: 'Workshop Manager — N1 (Resp. Atelier)',           short: 'N1 TECH' },
  { value: 'ROLE_VALIDATEUR_N2_TECH',   label: 'Technical Director — N2 (Directeur Technique)',    short: 'N2 TECH' },
]

// Roles that require a department assignment (used by the user form).
export const DEPT_REQUIRED_ROLES = new Set<string>([
  'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG', 'ROLE_VALIDATEUR_N1_INFO',
  'ROLE_VALIDATEUR_N2_INFO', 'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA', 'ROLE_VALIDATEUR_N2_INFRA',
  'ROLE_VALIDATEUR_N1_TECH', 'ROLE_VALIDATEUR_N2_TECH',
])
