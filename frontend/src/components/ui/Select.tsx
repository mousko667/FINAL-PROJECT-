import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * <select> natif stylé (Lot 1 / design-system) — pas de dropdown custom, pas de
 * Radix. Même grammaire visuelle qu'Input ; chevron via background SVG inline.
 * Les <option> sont fournies en children par le consommateur. Aucun texte en dur.
 *
 * Couleur du chevron : portée par la classe utilitaire `.select-chevron`
 * (index.css), qui commute son SVG data-URI light/dark via `.dark .select-chevron`.
 * Un data-URI SVG ne peut pas lire une CSS variable — la couleur ne peut donc
 * pas venir d'un style inline ici ; voir le commentaire dans index.css.
 */
export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: boolean
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, children, style, ...props }, ref) => (
    <select
      ref={ref}
      aria-invalid={error || undefined}
      style={{
        backgroundRepeat: 'no-repeat',
        backgroundPosition: 'right 0.75rem center',
        ...style,
      }}
      className={cn(
        'select-chevron flex h-10 w-full appearance-none rounded-[4px] border bg-surface pl-3 pr-9 py-2 text-sm text-ink',
        'transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed disabled:opacity-50',
        error
          ? 'border-crit focus-visible:ring-crit'
          : 'border-hairline focus-visible:ring-ring',
        className
      )}
      {...props}
    >
      {children}
    </select>
  )
)
Select.displayName = 'Select'
