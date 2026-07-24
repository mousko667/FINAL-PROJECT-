import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import fr from './fr.json'
import en from './en.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      fr: { translation: fr },
      en: { translation: en },
    },
    // AUDIT-042: no `lng` here. Setting it overrode LanguageDetector, so the stored
    // choice was read then discarded and English never survived a reload.
    // `fallbackLng` alone already makes French the default.
    fallbackLng: 'fr',
    // Only `fr` and `en` resources exist. Without this, a browser reporting a
    // regional tag (`en-US`, `en-GB`, `fr-CA`) resolves to a language with no
    // catalog at all, and every key falls back to French.
    load: 'languageOnly',
    supportedLngs: ['fr', 'en'],
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },
  })

/**
 * AUDIT-021: keeps `<html lang>` in step with the active language.
 *
 * The attribute was frozen at `en` in index.html and never updated, so native
 * `<input type="date">` widgets followed the browser locale instead of the app's,
 * and screen readers read French text with English phonetics (WCAG 3.1.1).
 * Same pattern as `useTheme`, which already drives documentElement.
 */
function syncDocumentLang(lng: string) {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lng.split('-')[0]
  }
}

syncDocumentLang(i18n.language || 'fr')
i18n.on('languageChanged', syncDocumentLang)

export default i18n
