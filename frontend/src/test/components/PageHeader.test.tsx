import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageHeader } from '@/components/ui/PageHeader'

describe('PageHeader', () => {
  it('rend le titre dans un <h1> (accessibilité, pas de texte en dur)', () => {
    render(<PageHeader title="Tableau de bord" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Tableau de bord' })).toBeInTheDocument()
  })

  it('rend le sous-titre quand il est fourni', () => {
    render(<PageHeader title="T" subtitle="Sous-titre" />)
    expect(screen.getByText('Sous-titre')).toBeInTheDocument()
  })

  it('rend la zone actions quand elle est fournie', () => {
    render(<PageHeader title="T" actions={<button>Exporter</button>} />)
    expect(screen.getByRole('button', { name: 'Exporter' })).toBeInTheDocument()
  })

  it('porte le dégradé navy et le filet or (via les tokens d\'application)', () => {
    const { container } = render(<PageHeader title="T" />)
    const root = container.firstChild as HTMLElement
    expect(root.className).toMatch(/from-header-grad-from/)
    expect(root.className).toMatch(/to-header-grad-to/)
    // le filet or est un élément dédié avec bg-header-accent
    expect(container.querySelector('.bg-header-accent')).not.toBeNull()
  })

  it('propage className', () => {
    const { container } = render(<PageHeader title="T" className="mb-8" />)
    expect((container.firstChild as HTMLElement).className).toMatch(/mb-8/)
  })
})
