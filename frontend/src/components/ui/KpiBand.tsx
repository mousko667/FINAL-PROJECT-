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

// Fond teinté (-bg) + barre latérale 4px de la teinte pleine, par ton.
// Une tuile SANS tone = informative → fond bleu-ardoise --kpi-info-bg + barre info.
const TONE_BG: Record<KpiTone, string> = {
  pos: 'bg-pos-bg border-l-4 border-l-pos',
  warn: 'bg-warn-bg border-l-4 border-l-warn',
  hot: 'bg-hot-bg border-l-4 border-l-hot',
  crit: 'bg-crit-bg border-l-4 border-l-crit',
  info: 'bg-kpi-info border-l-4 border-l-info',
}
const TONE_TEXT: Record<KpiTone, string> = {
  pos: 'text-pos', warn: 'text-warn', hot: 'text-hot', crit: 'text-crit', info: 'text-ink',
}
const DEFAULT_TILE = 'bg-kpi-info border-l-4 border-l-info'

/**
 * Unified KPI band (Track B / Lot B2, enrichi Lot couleur). UN conteneur bordé
 * avec séparateurs internes. Chaque tuile porte un fond teinté selon son `tone` :
 * pos/warn/hot/crit = fond -bg + barre de la teinte pleine (SENS d'alerte, piloté
 * par la page) ; sans tone = fond bleu-ardoise informatif. La couleur ne décore
 * jamais sans signification.
 */
export function KpiBand({ items, className }: KpiBandProps) {
  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row',
        'rounded-[4px] border border-hairline shadow-sm overflow-hidden',
        'divide-y sm:divide-y-0 sm:divide-x divide-hairline',
        className
      )}
    >
      {items.map((item, i) => (
        <div
          key={i}
          data-kpi-tile
          className={cn('flex-1 min-w-0 px-5 py-4', item.tone ? TONE_BG[item.tone] : DEFAULT_TILE)}
        >
          <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
            {item.label}
          </p>
          <p className={cn('num text-2xl font-semibold mt-1', item.tone ? TONE_TEXT[item.tone] : 'text-ink')}>
            {item.value}
          </p>
          {item.hint && <p className="text-xs text-ink-soft mt-1">{item.hint}</p>}
        </div>
      ))}
    </div>
  )
}
