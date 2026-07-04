const FR = 'fr-FR'

/** Montant numérique en séparateurs FR (espace insécable), sans devise. */
export const formatAmount = (n: number | string | null | undefined): string =>
  n == null || n === '' || isNaN(Number(n)) ? '—' : new Intl.NumberFormat(FR).format(Number(n))

/** Date seule JJ/MM/AAAA. Accepte Date | ISO string | null. */
export const formatDate = (d: string | number | Date | null | undefined): string => {
  if (d == null || d === '') return '—'
  const dt = new Date(d)
  return isNaN(dt.getTime()) ? '—' : dt.toLocaleDateString(FR)
}

/** Date + heure (JJ/MM/AAAA HH:mm) format FR 24h. */
export const formatDateTime = (d: string | number | Date | null | undefined): string => {
  if (d == null || d === '') return '—'
  const dt = new Date(d)
  return isNaN(dt.getTime()) ? '—' : dt.toLocaleString(FR)
}
