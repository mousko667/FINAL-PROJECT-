import { NavLink, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAppSelector } from '@/store/hooks'
import { RoleGuard } from '@/components/auth/RoleGuard'
import {
  LayoutDashboard,
  FileText,
  BarChart3,
  Users,
  Building2,
  ScrollText,
  Truck,
  UserCircle,
  Shield,
  Zap,
  GitBranch,
  DollarSign,
  Bell,
  Package,
  Archive,
  ChevronRight,
  Container,
  UserCheck,
  KeyRound,
  SlidersHorizontal,
  Megaphone,
  ShieldAlert,
  ListChecks,
  AlarmClock,
  Clock,
  Trash2,
  ShieldCheck,
  GitCompare,
  HardDrive,
} from 'lucide-react'
import { cn } from '@/lib/utils'

function NavItem({
  to,
  icon: Icon,
  label,
  end = false,
}: {
  to: string
  icon: React.ElementType
  label: string
  end?: boolean
}) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 px-3 py-2.5 rounded-[4px] text-sm font-medium transition-all duration-150 group',
          isActive
            ? 'oct-nav-active pl-[9px]'
            : 'text-slate-300 hover:bg-white/10 hover:text-white'
        )
      }
    >
      <Icon className="w-4 h-4 shrink-0 transition-transform duration-150 group-hover:scale-105" />
      <span className="truncate">{label}</span>
    </NavLink>
  )
}

function SectionLabel({ label }: { label: string }) {
  return (
    <p className="px-3 pt-4 pb-1 text-[10px] font-bold uppercase tracking-widest text-slate-400">
      {label}
    </p>
  )
}

export default function Sidebar() {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)

  const roleLabel = (() => {
    const roles = user?.roles ?? []
    if (roles.includes('ROLE_ADMIN')) return t('role.admin', 'Administrateur')
    if (roles.includes('ROLE_DAF')) return t('role.daf', 'DAF')
    if (roles.includes('ROLE_ASSISTANT_COMPTABLE')) return t('role.assistant_comptable', 'Ass. Comptable')
    const v = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N1_'))
    if (v) return `${t('role.validateur_n1', 'Validateur N1')} — ${v.replace('ROLE_VALIDATEUR_N1_', '')}`
    const v2 = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N2_'))
    if (v2) return `${t('role.validateur_n2', 'Validateur N2')} — ${v2.replace('ROLE_VALIDATEUR_N2_', '')}`
    return ''
  })()

  return (
    <aside className="w-64 flex flex-col shrink-0 bg-oct-navy text-white shadow-xl">
      {/* Logo */}
      <div className="px-5 py-5 border-b border-white/10">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-[4px] bg-oct-gold flex items-center justify-center shrink-0">
            <Container className="w-4 h-4 text-oct-navy" />
          </div>
          <div className="min-w-0">
            <h1 className="text-sm font-bold text-white leading-tight">OCT Invoices</h1>
            <p className="text-[10px] text-slate-400 leading-tight truncate">Owendo Container Terminal</p>
          </div>
        </div>
        {user && (
          <div className="mt-3.5 px-2 py-2 rounded-[4px] bg-white/5 border border-white/10">
            <p className="text-xs font-semibold text-white truncate">{user.username}</p>
            {roleLabel && <p className="text-[10px] text-slate-400 mt-0.5 truncate">{roleLabel}</p>}
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-3 overflow-y-auto space-y-0.5 scrollbar-none">
        <NavItem to="/dashboard" icon={LayoutDashboard} label={t('nav.dashboard')} end />

        {/* AA + DAF: Purchase Orders */}
        <RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF', 'ROLE_ADMIN']} fallback={null}>
          <NavItem to="/purchase-orders" icon={FileText} label={t('nav.purchaseOrders', 'Purchase Orders')} />
        </RoleGuard>

        {/* Staff: Invoices */}
        <RoleGuard allowedRoles={[
          'ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE',
          'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG',
          'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO',
          'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
          'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA',
          'ROLE_VALIDATEUR_N2_INFRA', 'ROLE_VALIDATEUR_N1_TECH',
          'ROLE_VALIDATEUR_N2_TECH',
        ]} fallback={null}>
          <NavItem to="/invoices" icon={FileText} label={t('nav.invoices')} />
        </RoleGuard>

        {/* Staff: Three-way Matching */}
        <RoleGuard allowedRoles={[
          'ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE',
          'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG',
          'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO',
          'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
          'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA',
          'ROLE_VALIDATEUR_N2_INFRA', 'ROLE_VALIDATEUR_N1_TECH',
          'ROLE_VALIDATEUR_N2_TECH',
        ]} fallback={null}>
          <NavItem to="/matching" icon={GitCompare} label={t('matching.pageTitle')} />
        </RoleGuard>

        {/* Validators: Approval Queue */}
        <RoleGuard allowedRoles={[
          'ROLE_DAF',
          'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG',
          'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO',
          'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
          'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA',
          'ROLE_VALIDATEUR_N2_INFRA', 'ROLE_VALIDATEUR_N1_TECH',
          'ROLE_VALIDATEUR_N2_TECH',
        ]} fallback={null}>
          <NavItem to="/approvals" icon={ScrollText} label={t('nav.approvals', 'File d\'approbation')} />
          <NavItem to="/my-delegations" icon={UserCheck} label={t('delegations.navMine', 'Mes délégations')} />
        </RoleGuard>

        {/* AA: Suppliers */}
        <RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE']} fallback={null}>
          <NavItem to="/admin/suppliers" icon={Truck} label={t('nav.suppliers')} />
        </RoleGuard>

        {/* Finance section */}
        <RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']} fallback={null}>
          <SectionLabel label="Finance" />
          <NavItem to="/payments" icon={DollarSign} label={t('nav.payments', 'Paiements')} />
          <NavItem to="/goods-receipts" icon={Package} label={t('nav.goodsReceipts', 'Bons de Réception')} />
        </RoleGuard>

        <RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF', 'ROLE_ADMIN']} fallback={null}>
          <NavItem to="/archive" icon={Archive} label={t('nav.archive', 'Archive')} />
        </RoleGuard>

        {/* Reports: DAF + AA */}
        <RoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE']} fallback={null}>
          <NavItem to="/reports" icon={BarChart3} label={t('nav.reports')} />
          <NavItem to="/reports/builder" icon={FileText} label={t('reportBuilder.navTitle', 'Constructeur de rapports')} />
        </RoleGuard>

        {/* Financial audit: DAF only */}
        <RoleGuard allowedRoles={['ROLE_DAF']} fallback={null}>
          <NavItem to="/financial-audit" icon={ScrollText} label={t('nav.financialAudit', 'Audit Financier')} />
        </RoleGuard>

        {/* Common */}
        <SectionLabel label="Compte" />
        <NavItem to="/notifications" icon={Bell} label={t('nav.notifications', 'Notifications')} />
        <NavItem to="/access-requests" icon={KeyRound} label={t('accessRequests.navMine', 'Mes demandes d\'accès')} />
        <NavItem to="/profile" icon={UserCircle} label={t('nav.profile', 'Profil')} />

        {/* Admin section */}
        <RoleGuard allowedRoles={['ROLE_ADMIN']} fallback={null}>
          <SectionLabel label={t('nav.admin')} />
          <NavItem to="/admin/users" icon={Users} label={t('nav.users')} />
          <NavItem to="/admin/permissions" icon={KeyRound} label={t('admin.permissions.navTitle', 'Matrice des permissions')} />
          <NavItem to="/admin/department-access" icon={Building2} label={t('nav.departmentAccess')} />
          <NavItem to="/admin/access-requests" icon={UserCheck} label={t('accessRequests.navAdmin', 'Demandes d\'accès')} />
          <NavItem to="/admin/announcements" icon={Megaphone} label={t('admin.announcements.navTitle', 'Annonces')} />
          <NavItem to="/admin/departments" icon={Building2} label={t('nav.departments')} />
          <NavItem to="/admin/suppliers" icon={Truck} label={t('nav.suppliers')} />
          <NavItem to="/admin/audit" icon={ScrollText} label={t('nav.auditLog')} />
          <NavItem to="/admin/approval-matrix" icon={GitBranch} label={t('admin.approvalMatrix.title', 'Matrice d\'approbation')} />
          <NavItem to="/admin/delegations" icon={UserCheck} label={t('admin.delegations.title', 'Délégations')} />
          <NavItem to="/admin/matching-config" icon={SlidersHorizontal} label={t('admin.matchingConfig.navTitle', 'Rapprochement')} />
          <NavItem to="/admin/retention-policy" icon={Clock} label={t('retentionPolicy.navTitle', 'Rétention')} />
          <NavItem to="/admin/archive-compliance" icon={ShieldCheck} label={t('archiveCompliance.navTitle', 'Conformité archives')} />
          <NavItem to="/admin/retention-disposition" icon={Trash2} label={t('retentionDisposition.navTitle', 'Purge')} />
          <NavItem to="/admin/checklist-templates" icon={ListChecks} label={t('checklist.navTitle', 'Checklists')} />
          <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']} fallback={null}>
            <NavItem to="/admin/escalation-rules" icon={AlarmClock} label={t('escalationRules.navTitle', 'Escalades')} />
          </RoleGuard>
          <NavItem to="/admin/security" icon={Shield} label={t('admin.security.title', 'Sécurité')} />
          <NavItem to="/admin/compliance" icon={ShieldAlert} label={t('admin.compliance.navTitle', 'Conformité')} />
          <NavItem to="/admin/backups" icon={HardDrive} label={t('admin.backups.navTitle', 'Sauvegardes')} />
          <NavItem to="/admin/integrations" icon={Zap} label={t('admin.integrations.title', 'Intégrations')} />
        </RoleGuard>
      </nav>

      {/* Footer */}
      <div className="px-4 py-3 border-t border-white/10 flex items-center justify-between">
        <span className="text-[10px] text-slate-400">v1.0.0 · OCT</span>
        <div className="w-1.5 h-1.5 rounded-full bg-pos" title={t('sidebar.systemOperational')} />
      </div>
    </aside>
  )
}
