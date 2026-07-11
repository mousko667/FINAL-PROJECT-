import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { Input } from '@/components/ui/Input'

describe('Input', () => {
  it('rend un champ texte accessible via son placeholder', () => {
    render(<Input placeholder="Référence" />)
    expect(screen.getByPlaceholderText('Référence')).toBeInTheDocument()
  })

  it('grammaire visuelle de base : surface, hairline, ring', () => {
    render(<Input aria-label="ref" />)
    const el = screen.getByLabelText('ref')
    expect(el.className).toMatch(/bg-surface/)
    expect(el.className).toMatch(/border-hairline/)
    expect(el.className).toMatch(/focus-visible:ring/)
  })

  it('state erreur : bordure crit + aria-invalid', () => {
    render(<Input aria-label="ref" error />)
    const el = screen.getByLabelText('ref')
    expect(el).toHaveAttribute('aria-invalid', 'true')
    expect(el.className).toMatch(/border-crit/)
  })

  it('forwarde la ref', () => {
    const ref = createRef<HTMLInputElement>()
    render(<Input ref={ref} aria-label="ref" />)
    expect(ref.current?.tagName).toBe('INPUT')
  })
})
