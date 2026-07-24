import '@testing-library/jest-dom'
import { vi } from 'vitest'

// The app detects its language from localStorage then `navigator` (AUDIT-042 removed
// the hardcoded `lng`, so detection is now genuinely in charge). jsdom reports
// `en-US`, which would make the UI language depend on the test runner's environment.
// Seeding the detector's key pins tests to French — the app's default — the same way
// matchMedia below neutralises another jsdom gap. Tests that need English call
// `i18n.changeLanguage('en')` explicitly.
localStorage.setItem('i18nextLng', 'fr')

// jsdom has no matchMedia; useTheme (and any prefers-color-scheme reader) needs it.
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}
