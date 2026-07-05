import { cn } from '@/lib/utils'

export type KpiTone = 'pos' | 'warn' | 'hot' | 'crit' | 'info'

export interface KpiBandItem {
  label: string
  value: string | number
  hint?: string
  tone?: KpiTone
}

interface KpiBandProps {
  items: KpiBandItem[]
  className?: string
}

const TONE_TEXT: Record<KpiTone, string> = {
  pos: 'text-pos',
  warn: 'text-warn',
  hot: 'text-hot',
  crit: 'text-crit',
  info: 'text-info',
}

/**
 * Unified KPI band (Track B / Lot B2): ONE bordered container with internal
 * vertical separators between items, instead of N separately-shadowed cards.
 * Wraps to a 2-column grid on small screens (separators become horizontal
 * via divide-y there) and lays out in a single row from `sm` up.
 */
export function KpiBand({ items, className }: KpiBandProps) {
  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row',
        'bg-ground rounded-[4px] border border-hairline shadow-sm',
        'divide-y sm:divide-y-0 sm:divide-x divide-hairline',
        className
      )}
    >
      {items.map((item, i) => (
        <div key={i} className="flex-1 min-w-0 px-5 py-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            {item.label}
          </p>
          <p className={cn('num text-2xl font-semibold text-ink mt-1', item.tone && TONE_TEXT[item.tone])}>
            {item.value}
          </p>
          {item.hint && (
            <p className="text-xs text-ink-soft mt-1">{item.hint}</p>
          )}
        </div>
      ))}
    </div>
  )
}
