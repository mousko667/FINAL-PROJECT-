import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Button } from '@/components/ui/Button'

describe('Button', () => {
  it('rend son contenu (libellé fourni par le consommateur, pas de texte en dur)', () => {
    render(<Button>Enregistrer</Button>)
    expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeInTheDocument()
  })

  it('applique la variante primary par défaut (navy)', () => {
    render(<Button>x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-oct-navy/)
  })

  it('applique la variante gold (CTA premium, gold-deep en fond)', () => {
    render(<Button variant="gold">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-gold-deep/)
  })

  it('applique la variante destructive (crit)', () => {
    render(<Button variant="destructive">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-crit/)
  })

  it('expose un ring de focus visible', () => {
    render(<Button>x</Button>)
    expect(screen.getByRole('button').className).toMatch(/focus-visible:ring/)
  })

  it('state loading : disabled + aria-busy + spinner', () => {
    render(<Button loading>x</Button>)
    const btn = screen.getByRole('button')
    expect(btn).toBeDisabled()
    expect(btn).toHaveAttribute('aria-busy', 'true')
  })

  it('forwarde la ref vers le <button>', () => {
    const ref = createRef<HTMLButtonElement>()
    render(<Button ref={ref}>x</Button>)
    expect(ref.current?.tagName).toBe('BUTTON')
  })

  it('fusionne une className extra', () => {
    render(<Button className="w-full">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/w-full/)
  })

  it('applique la variante secondary (surface + bordure hairline)', () => {
    render(<Button variant="secondary">x</Button>)
    const cls = screen.getByRole('button').className
    expect(cls).toMatch(/bg-surface/)
    expect(cls).toMatch(/border-hairline/)
  })

  it('applique la variante ghost (fond transparent)', () => {
    render(<Button variant="ghost">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-transparent/)
  })

  it('applique la taille sm (h-8)', () => {
    render(<Button size="sm">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/h-8/)
  })

  it('applique la taille lg (h-12)', () => {
    render(<Button size="lg">x</Button>)
    expect(screen.getByRole('button').className).toMatch(/h-12/)
  })

  it('applique la taille icon (carré h-10 w-10)', () => {
    render(<Button size="icon" aria-label="fermer">x</Button>)
    const cls = screen.getByRole('button', { name: 'fermer' }).className
    expect(cls).toMatch(/h-10/)
    expect(cls).toMatch(/w-10/)
  })

  it('affiche un spinner animé en état loading', () => {
    const { container } = render(<Button loading>x</Button>)
    expect(container.querySelector('svg.animate-spin')).not.toBeNull()
  })
})
