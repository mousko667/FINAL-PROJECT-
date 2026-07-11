import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Champ de saisie partagé (Lot 1 / design-system). Natif stylé. Grammaire :
 * fond surface, bordure hairline, texte ink, placeholder ink-faint, ring navy.
 * `error` → bordure/ring crit + aria-invalid. Aucun texte en dur.
 */
export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, error, ...props }, ref) => (
    <input
      ref={ref}
      aria-invalid={error || undefined}
      className={cn(
        'flex h-10 w-full rounded-[4px] border bg-surface px-3 py-2 text-sm text-ink',
        'placeholder:text-ink-faint transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed disabled:opacity-50',
        error
          ? 'border-crit focus-visible:ring-crit'
          : 'border-hairline focus-visible:ring-ring',
        className
      )}
      {...props}
    />
  )
)
Input.displayName = 'Input'
