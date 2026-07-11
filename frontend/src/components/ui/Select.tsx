import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * <select> natif stylé (Lot 1 / design-system) — pas de dropdown custom, pas de
 * Radix. Même grammaire visuelle qu'Input ; chevron via background SVG inline.
 * Les <option> sont fournies en children par le consommateur. Aucun texte en dur.
 */
const CHEVRON =
  "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236B6456' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>\")"

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: boolean
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, children, style, ...props }, ref) => (
    <select
      ref={ref}
      aria-invalid={error || undefined}
      style={{
        backgroundImage: CHEVRON,
        backgroundRepeat: 'no-repeat',
        backgroundPosition: 'right 0.75rem center',
        ...style,
      }}
      className={cn(
        'flex h-10 w-full appearance-none rounded-[4px] border bg-surface pl-3 pr-9 py-2 text-sm text-ink',
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
