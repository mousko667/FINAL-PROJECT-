import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface PanelProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  title?: ReactNode
  className?: string
}

/**
 * Generic panel/card wrapper (Track B / Lot B2) — replaces the repeated
 * `bg-white rounded-xl border` pattern across pages. Thin hairline border,
 * 4px radius, near-flat shadow. Uses the dedicated `--surface` token (see
 * index.css) rather than shadcn's `--card`, since `--card` has no dark-mode
 * override. Adoption across pages happens in B4/B5 — this lot only creates
 * and tests it.
 */
export function Panel({ children, title, className, ...props }: PanelProps) {
  return (
    <div
      className={cn('bg-surface rounded-[4px] border border-hairline shadow-sm', className)}
      {...props}
    >
      {title && (
        <div className="px-5 py-4 border-b border-hairline">
          <h2 className="font-semibold text-ink">{title}</h2>
        </div>
      )}
      {title ? <div className="p-5">{children}</div> : children}
    </div>
  )
}
