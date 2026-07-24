import { describe, it, expect, afterEach } from 'vitest'
import i18n from '@/i18n'
import { formatAmount, formatDate, formatDateTime, currentLocale } from '@/lib/format'

// Intl uses NARROW NO-BREAK SPACE (U+202F) or NBSP (U+00A0) as the fr-FR group
// separator depending on the ICU build — normalise before asserting.
const norm = (s: string) => s.replace(/[  ]/g, ' ')

describe('AUDIT-043 — the format helper must follow the active language', () => {
  afterEach(async () => {
    await i18n.changeLanguage('fr')
  })

  it('formats amounts with French separators in French', async () => {
    await i18n.changeLanguage('fr')
    expect(currentLocale()).toBe('fr-FR')
    // French groups with a space, never a comma.
    expect(norm(formatAmount(1500000))).toBe('1 500 000')
  })

  it('formats amounts with US separators in English — this was frozen to fr-FR', async () => {
    await i18n.changeLanguage('en')
    expect(currentLocale()).toBe('en-US')
    expect(formatAmount(1500000)).toBe('1,500,000')
  })

  it('formats dates per locale: DD/MM/YYYY in French, M/D/YYYY in English', async () => {
    await i18n.changeLanguage('fr')
    expect(formatDate('2026-06-01T00:00:00Z')).toBe('01/06/2026')

    await i18n.changeLanguage('en')
    expect(formatDate('2026-06-01T00:00:00Z')).toBe('6/1/2026')
  })

  it('formats date-times per locale as well', async () => {
    await i18n.changeLanguage('fr')
    const fr = formatDateTime('2026-06-01T10:30:00Z')
    await i18n.changeLanguage('en')
    const en = formatDateTime('2026-06-01T10:30:00Z')
    // The two locales must not render identically — that was the defect.
    expect(fr).not.toBe(en)
    expect(en).toMatch(/AM|PM/)
  })

  it('keeps the em dash for empty and invalid values in both locales', async () => {
    for (const lng of ['fr', 'en']) {
      await i18n.changeLanguage(lng)
      expect(formatAmount(null)).toBe('—')
      expect(formatAmount('abc')).toBe('—')
      expect(formatDate(null)).toBe('—')
      expect(formatDate('not-a-date')).toBe('—')
      expect(formatDateTime(null)).toBe('—')
    }
  })
})

describe('AUDIT-021 / AUDIT-042 — language decision chain', () => {
  afterEach(async () => {
    await i18n.changeLanguage('fr')
  })

  it('keeps <html lang> in step with the active language', async () => {
    await i18n.changeLanguage('en')
    expect(document.documentElement.lang).toBe('en')

    await i18n.changeLanguage('fr')
    expect(document.documentElement.lang).toBe('fr')
  })

  it('persists the chosen language to localStorage so it survives a reload', async () => {
    await i18n.changeLanguage('en')
    expect(localStorage.getItem('i18nextLng')).toBe('en')
  })

  it('does NOT hardcode a startup language — that is what discarded the stored choice', () => {
    // `lng` short-circuits LanguageDetector: it was set to 'fr', so a stored 'en'
    // was read from localStorage and then immediately overridden on every load.
    expect(i18n.options.lng).toBeUndefined()
    // French stays the default through fallbackLng alone.
    expect(i18n.options.fallbackLng).toEqual(['fr'])
    expect(i18n.options.detection?.caches).toContain('localStorage')
  })
})
