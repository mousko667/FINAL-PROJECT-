import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Bouton partagé (Lot 1 / design-system). Natif stylé, zéro dépendance nouvelle.
 * `primary` = navy (structure) ; `gold` = CTA premium rare, seul endroit où le
 * gold vit en fond (--gold-deep). Aucun texte en dur : le libellé vient du
 * consommateur (i18n côté appelant). focus-visible ring navy (--ring).
 */
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-[4px] font-medium ' +
    'transition-colors focus-visible:outline-none focus-visible:ring-2 ' +
    'focus-visible:ring-ring focus-visible:ring-offset-2 ' +
    'disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary:
          'bg-gradient-to-b from-oct-navy-light to-oct-navy text-white hover:to-oct-navy-light',
        secondary:
          'bg-info-bg text-info border border-info/40 hover:bg-info/15',
        ghost: 'bg-transparent text-info hover:bg-info/10',
        destructive: 'bg-crit text-white hover:bg-crit/90',
        gold: 'bg-gold-deep text-oct-navy-dark hover:bg-gold-deep/90',
      },
      size: {
        sm: 'h-8 px-3 text-xs',
        md: 'h-10 px-4 text-sm',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
)
Button.displayName = 'Button'

export { buttonVariants }
