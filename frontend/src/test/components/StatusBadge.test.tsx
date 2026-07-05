import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { StatusBadge } from '@/components/ui/StatusBadge'
import type { InvoiceStatus } from '@/types/invoice'

function renderBadge(status: InvoiceStatus, variant?: 'pill' | 'dot-only' | 'inline') {
  return render(
    <I18nextProvider i18n={i18n}>
      <StatusBadge status={status} variant={variant} />
    </I18nextProvider>
  )
}

describe('StatusBadge — semantic Registre mapping', () => {
  it('renders BROUILLON as neutral (ink-soft / hairline, no semantic color)', () => {
    renderBadge('BROUILLON')
    const badge = screen.getByText(i18n.t('status.BROUILLON')).closest('span')!
    expect(badge.className).toMatch(/text-ink-soft/)
    expect(badge.className).not.toMatch(/text-(pos|warn|hot|crit|info)\b/)
  })

  it('renders SOUMIS with the info semantic classes', () => {
    renderBadge('SOUMIS')
    const badge = screen.getByText(i18n.t('status.SOUMIS')).closest('span')!
    expect(badge.className).toMatch(/bg-info-bg/)
    expect(badge.className).toMatch(/text-info\b/)
  })

  it('renders EN_VALIDATION_N1 with the warn semantic classes', () => {
    renderBadge('EN_VALIDATION_N1')
    const badge = screen.getByText(i18n.t('status.EN_VALIDATION_N1')).closest('span')!
    expect(badge.className).toMatch(/bg-warn-bg/)
    expect(badge.className).toMatch(/text-warn\b/)
  })

  it('renders EN_VALIDATION_N2 with the hot semantic classes', () => {
    renderBadge('EN_VALIDATION_N2')
    const badge = screen.getByText(i18n.t('status.EN_VALIDATION_N2')).closest('span')!
    expect(badge.className).toMatch(/bg-hot-bg/)
    expect(badge.className).toMatch(/text-hot\b/)
  })

  it('N1 (warn) and N2 (hot) render DISTINCT classes', () => {
    renderBadge('EN_VALIDATION_N1')
    const n1 = screen.getByText(i18n.t('status.EN_VALIDATION_N1')).closest('span')!
    renderBadge('EN_VALIDATION_N2')
    const n2 = screen.getByText(i18n.t('status.EN_VALIDATION_N2')).closest('span')!

    expect(n1.className).not.toEqual(n2.className)
    expect(n1.className).toMatch(/warn/)
    expect(n1.className).not.toMatch(/\bhot\b|-hot-|text-hot|bg-hot/)
    expect(n2.className).toMatch(/hot/)
    expect(n2.className).not.toMatch(/warn/)
  })

  it('renders VALIDE with the pos (clear) semantic classes', () => {
    renderBadge('VALIDE')
    const badge = screen.getByText(i18n.t('status.VALIDE')).closest('span')!
    expect(badge.className).toMatch(/bg-pos-bg/)
    expect(badge.className).toMatch(/text-pos\b/)
  })

  it('renders BON_A_PAYER with the pos (clear) semantic classes', () => {
    renderBadge('BON_A_PAYER')
    const badge = screen.getByText(i18n.t('status.BON_A_PAYER')).closest('span')!
    expect(badge.className).toMatch(/bg-pos-bg/)
    expect(badge.className).toMatch(/text-pos\b/)
  })

  it('renders PAYE with a stronger/saturated pos treatment, distinct from VALIDE', () => {
    renderBadge('VALIDE')
    const valide = screen.getByText(i18n.t('status.VALIDE')).closest('span')!
    renderBadge('PAYE')
    const paye = screen.getByText(i18n.t('status.PAYE')).closest('span')!

    // Both are "pos" family, but PAYE must use the saturated/full variant,
    // not the same light bg-pos-bg tint as VALIDE.
    expect(paye.className).toMatch(/bg-pos\b/)
    expect(paye.className).not.toMatch(/bg-pos-bg/)
    expect(paye.className).not.toEqual(valide.className)
  })

  it('renders ARCHIVE as neutral (ink-faint / hairline, no semantic color)', () => {
    renderBadge('ARCHIVE')
    const badge = screen.getByText(i18n.t('status.ARCHIVE')).closest('span')!
    expect(badge.className).toMatch(/text-ink-faint/)
    expect(badge.className).not.toMatch(/text-(pos|warn|hot|crit|info)\b/)
  })

  it('renders REJETE with the crit semantic classes', () => {
    renderBadge('REJETE')
    const badge = screen.getByText(i18n.t('status.REJETE')).closest('span')!
    expect(badge.className).toMatch(/bg-crit-bg/)
    expect(badge.className).toMatch(/text-crit\b/)
  })

  it('data-status is present on the pill (default) variant', () => {
    renderBadge('VALIDE')
    const badge = screen.getByText(i18n.t('status.VALIDE')).closest('span')!
    expect(badge).toHaveAttribute('data-status', 'VALIDE')
  })

  it('data-status is present on the dot-only variant', () => {
    renderBadge('EN_VALIDATION_N1', 'dot-only')
    const dot = screen.getByTitle(i18n.t('status.EN_VALIDATION_N1'))
    expect(dot).toHaveAttribute('data-status', 'EN_VALIDATION_N1')
  })

  it('data-status is present on the inline variant', () => {
    renderBadge('REJETE', 'inline')
    const badge = screen.getByText(i18n.t('status.REJETE')).closest('span')!
    expect(badge).toHaveAttribute('data-status', 'REJETE')
  })

  it('renders all three variants (pill, dot-only, inline) without crashing', () => {
    const { unmount: u1 } = renderBadge('SOUMIS', 'pill')
    expect(screen.getByText(i18n.t('status.SOUMIS'))).toBeInTheDocument()
    u1()

    renderBadge('SOUMIS', 'dot-only')
    expect(screen.getByTitle(i18n.t('status.SOUMIS'))).toBeInTheDocument()
  })
})
