import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Set de sous-composants table partagés (Lot 1 / design-system). Zéro logique
 * métier. En-tête ink-soft uppercase fin ; lignes séparées border-hairline,
 * hover hairline/40. Montants monétaires : ajouter className="num" sur le TD.
 * Le conteneur overflow-x-auto évite tout scroll horizontal de page.
 */
export const Table = React.forwardRef<
  HTMLTableElement,
  React.TableHTMLAttributes<HTMLTableElement> & { containerClassName?: string }
>(({ className, containerClassName, ...props }, ref) => (
  <div className={cn('w-full overflow-x-auto', containerClassName)}>
    <table ref={ref} className={cn('w-full border-collapse text-sm', className)} {...props} />
  </div>
))
Table.displayName = 'Table'

export const THead = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <thead ref={ref} className={cn('border-b border-hairline', className)} {...props} />
))
THead.displayName = 'THead'

export const TBody = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tbody ref={ref} className={className} {...props} />
))
TBody.displayName = 'TBody'

export const TR = React.forwardRef<
  HTMLTableRowElement,
  React.HTMLAttributes<HTMLTableRowElement>
>(({ className, ...props }, ref) => (
  <tr
    ref={ref}
    className={cn('border-b border-hairline transition-colors hover:bg-hairline/40', className)}
    {...props}
  />
))
TR.displayName = 'TR'

export const TH = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      'px-4 py-2.5 text-left align-middle text-xs font-medium uppercase tracking-wide text-ink-soft',
      className
    )}
    {...props}
  />
))
TH.displayName = 'TH'

export const TD = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <td ref={ref} className={cn('px-4 py-3 align-middle text-ink', className)} {...props} />
))
TD.displayName = 'TD'
