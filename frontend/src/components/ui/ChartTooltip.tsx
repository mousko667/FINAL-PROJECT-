import { cn } from '@/lib/utils'

/**
 * Tooltip Recharts thématisée (Lot 1 / design-system). Passée via
 * content={<ChartTooltip valueFormatter={...} />}. Surface + hairline + ink ;
 * pastille de série ; montants en .num (via valueFormatter côté appelant, ou
 * la classe .num sur la valeur). Le texte porte des tokens d'encre, jamais la
 * couleur de série (celle-ci n'est que la pastille). Aucun texte en dur.
 */
export interface ChartTooltipProps {
  active?: boolean
  payload?: Array<{ name?: string; value?: number | string; color?: string }>
  label?: string | number
  valueFormatter?: (v: number | string) => string
}

export function ChartTooltip({ active, payload, label, valueFormatter }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) return null
  return (
    <div className={cn('bg-surface border border-hairline rounded-[4px] shadow-sm px-3 py-2 text-xs')}>
      {label !== undefined && <div className="mb-1 font-medium text-ink">{label}</div>}
      <ul className="space-y-0.5">
        {payload.map((entry, i) => (
          <li key={i} className="flex items-center gap-2 text-ink-soft">
            <span
              className="inline-block w-2 h-2 rounded-full shrink-0"
              style={{ backgroundColor: entry.color }}
              aria-hidden="true"
            />
            <span>{entry.name}</span>
            <span className="num ml-auto text-ink">
              {entry.value !== undefined && valueFormatter
                ? valueFormatter(entry.value)
                : entry.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
