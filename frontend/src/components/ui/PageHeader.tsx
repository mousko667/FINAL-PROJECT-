import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface PageHeaderProps {
  title: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  className?: string
}

/**
 * En-tête de page partagé (Lot couleur "Soutenu"). Bandeau titre en dégradé navy
 * (--header-grad-from → --header-grad-to) avec un filet or de 3px en bas
 * (--header-accent). Titre en blanc, sous-titre en or atténué. Remplace le pattern
 * `<div><h1 class="text-2xl font-bold text-ink">…</h1><p>…</p></div>` inline des pages.
 * Aucun texte en dur : title/subtitle viennent de l'appelant (i18n côté page).
 */
export function PageHeader({ title, subtitle, actions, className }: PageHeaderProps) {
  return (
    <div
      className={cn(
        'relative rounded-[4px] bg-gradient-to-r from-header-grad-from to-header-grad-to',
        'px-6 py-5',
        className
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-white">{title}</h1>
          {subtitle && (
            <p className="text-sm text-oct-gold-light/90 mt-1">{subtitle}</p>
          )}
        </div>
        {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
      </div>
      {/* Filet or 3px en bas */}
      <div className="absolute inset-x-0 bottom-0 h-[3px] bg-header-accent rounded-b-[4px]" />
    </div>
  )
}
