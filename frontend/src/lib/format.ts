// The bare i18next instance, NOT `@/i18n`: importing the app's init module would
// drag `initReactI18next` and the whole catalog into every consumer — including
// tests that mock `react-i18next`. Both modules share the same singleton, so the
// active language read here is the same one the app sets.
import i18n from 'i18next'

/**
 * Locale actually in use, derived from the active language.
 *
 * AUDIT-043: this helper is the one 29 files rely on, and it used to force
 * `fr-FR` regardless of the language — an English session showed English labels
 * next to French-formatted amounts. Reading `i18n.language` at call time (not at
 * module load) makes every call site follow the language switch.
 */
export const currentLocale = (): string => (i18n.language?.startsWith('en') ? 'en-US' : 'fr-FR')

/** Montant numérique dans les séparateurs de la langue active, sans devise. */
export const formatAmount = (n: number | string | null | undefined): string =>
  n == null || n === '' || isNaN(Number(n)) ? '—' : new Intl.NumberFormat(currentLocale()).format(Number(n))

/** Date seule, dans le format de la langue active. Accepte Date | ISO string | null. */
export const formatDate = (d: string | number | Date | null | undefined): string => {
  if (d == null || d === '') return '—'
  const dt = new Date(d)
  return isNaN(dt.getTime()) ? '—' : dt.toLocaleDateString(currentLocale())
}

/** Date + heure, dans le format de la langue active. */
export const formatDateTime = (d: string | number | Date | null | undefined): string => {
  if (d == null || d === '') return '—'
  const dt = new Date(d)
  return isNaN(dt.getTime()) ? '—' : dt.toLocaleString(currentLocale())
}
