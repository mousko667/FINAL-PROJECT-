import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { KpiBand } from '@/components/ui/KpiBand'

describe('KpiBand', () => {
  it('une tuile sans tone prend le fond informatif (bleu-ardoise)', () => {
    const { container } = render(<KpiBand items={[{ label: 'Total', value: 42 }]} />)
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-kpi-info/)
  })

  it('une tuile tone=crit prend le fond crit-bg + barre latérale crit', () => {
    const { container } = render(
      <KpiBand items={[{ label: 'En retard', value: 3, tone: 'crit' }]} />
    )
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-crit-bg/)
    expect(tile.className).toMatch(/border-l-4/)
    expect(tile.className).toMatch(/border-l-crit/)
  })

  it('une tuile tone=pos prend le fond pos-bg + barre latérale pos', () => {
    const { container } = render(
      <KpiBand items={[{ label: 'Conformité', value: 'OK', tone: 'pos' }]} />
    )
    const tile = container.querySelector('[data-kpi-tile]') as HTMLElement
    expect(tile.className).toMatch(/bg-pos-bg/)
    expect(tile.className).toMatch(/border-l-pos/)
  })

  it('affiche label, valeur et hint', () => {
    const { getByText } = render(
      <KpiBand items={[{ label: 'Total', value: 42, hint: 'ce mois' }]} />
    )
    expect(getByText('Total')).toBeInTheDocument()
    expect(getByText('42')).toBeInTheDocument()
    expect(getByText('ce mois')).toBeInTheDocument()
  })
})
