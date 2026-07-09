import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { KpiBand } from '@/components/ui/KpiBand'

describe('KpiBand', () => {
  it('renders one item per entry (label, value, hint)', () => {
    render(
      <KpiBand
        items={[
          { label: 'Total factures', value: 128, hint: 'ce mois' },
          { label: 'En retard', value: 4 },
        ]}
      />
    )
    expect(screen.getByText('Total factures')).toBeInTheDocument()
    expect(screen.getByText('128')).toBeInTheDocument()
    expect(screen.getByText('ce mois')).toBeInTheDocument()
    expect(screen.getByText('En retard')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('renders a single shared container, not one card per item', () => {
    const { container } = render(
      <KpiBand
        items={[
          { label: 'A', value: 1 },
          { label: 'B', value: 2 },
          { label: 'C', value: 3 },
        ]}
      />
    )
    // Exactly one bordered band wrapper at the top level.
    expect(container.children).toHaveLength(1)
    expect(container.firstElementChild).toHaveClass('border-hairline')
  })

  it('applies the semantic tone class to the value when tone is set', () => {
    render(<KpiBand items={[{ label: 'Alertes', value: 3, tone: 'crit' }]} />)
    const value = screen.getByText('3')
    expect(value.className).toMatch(/text-crit/)
  })

  it('puts the .num class on the value for tabular figures', () => {
    render(<KpiBand items={[{ label: 'Montant', value: '1 234 500 XAF' }]} />)
    const value = screen.getByText('1 234 500 XAF')
    expect(value.className).toMatch(/\bnum\b/)
  })

  it('does not apply a tone class when tone is omitted', () => {
    render(<KpiBand items={[{ label: 'Sans teinte', value: 7 }]} />)
    const value = screen.getByText('7')
    expect(value.className).not.toMatch(/text-(pos|warn|hot|crit|info)\b/)
  })
})
