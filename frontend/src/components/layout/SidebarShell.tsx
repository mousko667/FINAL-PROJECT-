import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'
import { useSidebarDrawer } from './SidebarDrawerContext'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { cn } from '@/lib/utils'

/**
 * Responsive wrapper shared by the staff sidebar and the supplier sidebar.
 *
 * AUDIT-019: below `md` the aside used to keep its 256 px and squeezed `main` down to
 * 134 px on a 390 px viewport. It is now hidden off-canvas and slides in as a drawer.
 * From `md` up the previous static column is preserved exactly.
 *
 * AUDIT-023 (dette a11y relevée en V3-A) : `-translate-x-full` sort le tiroir de
 * l'écran mais le laisse dans l'ordre de tabulation — au clavier, un utilisateur
 * mobile traversait une vingtaine de liens invisibles avant d'atteindre le contenu.
 * `inert` neutralise le sous-arbre, mais uniquement sous `md` : au-dessus la barre
 * est une colonne statique visible, qui doit rester navigable.
 */
export function SidebarShell({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation()
  const { isOpen, close } = useSidebarDrawer()
  const isDesktop = useMediaQuery('(min-width: 768px)')
  const isHiddenDrawer = !isDesktop && !isOpen

  return (
    <>
      {/* Scrim — mobile only, closes the drawer on tap */}
      <div
        data-testid="sidebar-scrim"
        aria-hidden="true"
        onClick={close}
        className={cn(
          'fixed inset-0 z-30 bg-black/50 md:hidden',
          isOpen ? 'block' : 'hidden'
        )}
      />

      <aside
        id="app-sidebar"
        data-testid="sidebar"
        inert={isHiddenDrawer || undefined}
        aria-hidden={isHiddenDrawer || undefined}
        className={cn(
          'w-64 flex flex-col shrink-0 bg-oct-navy text-white shadow-xl',
          // Mobile: off-canvas drawer.
          'fixed inset-y-0 left-0 z-40 transition-transform duration-200',
          isOpen ? 'translate-x-0' : '-translate-x-full',
          // md and up: the original static column, always visible.
          'md:static md:translate-x-0 md:z-auto'
        )}
      >
        <button
          type="button"
          onClick={close}
          aria-label={t('nav.closeMenu', 'Fermer le menu')}
          className="md:hidden absolute top-3 right-3 p-2 rounded-[4px] text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
        {children}
      </aside>
    </>
  )
}
