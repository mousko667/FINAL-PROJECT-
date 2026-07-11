import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Select } from '@/components/ui/Select'

describe('Select', () => {
  it('rend un <select> natif avec les options fournies', () => {
    render(
      <Select aria-label="statut">
        <option value="a">A</option>
        <option value="b">B</option>
      </Select>
    )
    const el = screen.getByLabelText('statut')
    expect(el.tagName).toBe('SELECT')
    expect(screen.getByRole('option', { name: 'A' })).toBeInTheDocument()
  })

  it('grammaire visuelle alignée sur Input : surface, hairline, ring', () => {
    render(<Select aria-label="s"><option>x</option></Select>)
    const el = screen.getByLabelText('s')
    expect(el.className).toMatch(/bg-surface/)
    expect(el.className).toMatch(/border-hairline/)
    expect(el.className).toMatch(/focus-visible:ring/)
  })

  it('state erreur : bordure crit + aria-invalid', () => {
    render(<Select aria-label="s" error><option>x</option></Select>)
    const el = screen.getByLabelText('s')
    expect(el).toHaveAttribute('aria-invalid', 'true')
    expect(el.className).toMatch(/border-crit/)
  })

  it('forwarde la ref', () => {
    const ref = createRef<HTMLSelectElement>()
    render(<Select ref={ref} aria-label="s"><option>x</option></Select>)
    expect(ref.current?.tagName).toBe('SELECT')
  })
})
