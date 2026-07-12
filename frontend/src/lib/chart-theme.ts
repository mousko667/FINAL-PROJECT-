/**
 * Thème charts (Lot 1 / design-system). Couche de thème au-dessus de Recharts —
 * pas de remplacement de lib. Axes/grille recessifs via CSS vars (suivent .dark).
 * Palette de séries d'ORDRE FIXE, distincte des couleurs de statut
 * (pos/warn/hot/crit/info réservées : #3E7C5A, #B5852A, #C4622E, #A6432E, #2F6690).
 * Validée par script :
 *   frontend/scripts/dataviz/validate_palette.js (light surface #FFFFFF, dark #171B22).
 * Résultat : ALL CHECKS PASS pour les deux modes (bande de clarté OKLCH, plancher
 * de chroma 0.10, séparation CVD adjacente, contraste vs surface). Voir
 * .superpowers/sdd/task-6-report.md pour la sortie complète du validateur.
 * Au-delà de 8 séries : replier sur "Other" / small multiples — jamais une hue générée.
 */

export const SERIES_PALETTE_LIGHT: string[] = [
  '#0266A0', '#9A7E2E', '#048250', '#7A4E8C', '#059583', '#3A6EB8', '#B0472E', '#359557',
]
export const SERIES_PALETTE_DARK: string[] = [
  '#1C87CD', '#9C7C10', '#04985E', '#9C68B2', '#059583', '#427FD7', '#CD5538', '#1C9851',
]

/** Couleur de série par index (l'entité, pas le rang). Au-delà de la longueur,
 *  replie sur le dernier slot plutôt que de générer/cycler une hue. */
export function getSeriesColor(index: number, dark = false): string {
  const palette = dark ? SERIES_PALETTE_DARK : SERIES_PALETTE_LIGHT
  const i = Math.max(0, Math.min(index, palette.length - 1))
  return palette[i]
}

/** Props recessives pour CartesianGrid — lignes fines hairline, pas de verticales. */
export const chartGridProps = {
  stroke: 'hsl(var(--hairline))',
  strokeDasharray: '3 3',
  vertical: false,
} as const

/** Props d'axe — ticks ink-faint, ligne d'axe hairline, pas de tickLine. */
export const chartAxisProps = {
  tick: { fontSize: 11, fill: 'hsl(var(--ink-faint))' },
  axisLine: { stroke: 'hsl(var(--hairline))' },
  tickLine: false,
} as const
