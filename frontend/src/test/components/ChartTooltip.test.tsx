import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartTooltip } from '@/components/ui/ChartTooltip'

describe('ChartTooltip', () => {
  it('ne rend rien quand inactif', () => {
    const { container } = render(<ChartTooltip active={false} payload={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('rend le label et les entrées de série quand actif', () => {
    render(
      <ChartTooltip
        active
        label="Janvier"
        payload={[{ name: 'Montant', value: 1000, color: '#2F6690' }]}
      />
    )
    expect(screen.getByText('Janvier')).toBeInTheDocument()
    expect(screen.getByText('Montant')).toBeInTheDocument()
    expect(screen.getByText('1000')).toBeInTheDocument()
  })

  it('applique valueFormatter aux montants', () => {
    render(
      <ChartTooltip
        active
        label="Janvier"
        payload={[{ name: 'Montant', value: 1000, color: '#2F6690' }]}
        valueFormatter={(v) => `${v} XAF`}
      />
    )
    expect(screen.getByText('1000 XAF')).toBeInTheDocument()
  })

  it('grammaire visuelle : surface + hairline', () => {
    const { container } = render(
      <ChartTooltip active label="x" payload={[{ name: 'S', value: 1, color: '#000' }]} />
    )
    expect((container.firstElementChild as HTMLElement).className).toMatch(/bg-surface/)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/border-hairline/)
  })
})
