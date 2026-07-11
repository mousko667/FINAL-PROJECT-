import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '@/components/ui/Card'

describe('Card', () => {
  it('rend la grammaire visuelle Panel (surface, radius 4px, hairline, shadow)', () => {
    const { container } = render(<Card>x</Card>)
    const root = container.firstElementChild as HTMLElement
    expect(root.className).toMatch(/bg-surface/)
    expect(root.className).toMatch(/rounded-\[4px\]/)
    expect(root.className).toMatch(/border-hairline/)
    expect(root.className).toMatch(/shadow-sm/)
  })

  it('compose header/title/content/footer', () => {
    render(
      <Card>
        <CardHeader><CardTitle>Résumé</CardTitle></CardHeader>
        <CardContent>Corps</CardContent>
        <CardFooter>Pied</CardFooter>
      </Card>
    )
    expect(screen.getByRole('heading', { name: 'Résumé' })).toBeInTheDocument()
    expect(screen.getByText('Corps')).toBeInTheDocument()
    expect(screen.getByText('Pied')).toBeInTheDocument()
  })

  it('fusionne une className extra', () => {
    const { container } = render(<Card className="mt-6">x</Card>)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/mt-6/)
  })
})
