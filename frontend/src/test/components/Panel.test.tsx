import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Panel } from '@/components/ui/Panel'

describe('Panel', () => {
  it('renders children', () => {
    render(<Panel>Contenu du panel</Panel>)
    expect(screen.getByText('Contenu du panel')).toBeInTheDocument()
  })

  it('applies the hairline border, 4px radius and flat shadow tokens', () => {
    const { container } = render(<Panel>x</Panel>)
    const root = container.firstElementChild as HTMLElement
    expect(root.className).toMatch(/border-hairline/)
    expect(root.className).toMatch(/rounded-\[4px\]/)
    expect(root.className).toMatch(/bg-surface/)
    expect(root.className).toMatch(/shadow-sm/)
  })

  it('renders an optional title as a heading with a separating border', () => {
    render(<Panel title="Résumé">Corps</Panel>)
    expect(screen.getByRole('heading', { name: 'Résumé' })).toBeInTheDocument()
    expect(screen.getByText('Corps')).toBeInTheDocument()
  })

  it('does not render a title block when title is omitted', () => {
    render(<Panel>Seul</Panel>)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })

  it('merges an extra className onto the root', () => {
    const { container } = render(<Panel className="mt-6">x</Panel>)
    const root = container.firstElementChild as HTMLElement
    expect(root.className).toMatch(/mt-6/)
  })
})
