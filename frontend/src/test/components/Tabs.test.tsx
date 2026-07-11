import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Tabs, TabList, Tab, TabPanel } from '@/components/ui/Tabs'

function Fixture() {
  return (
    <Tabs defaultValue="a">
      <TabList>
        <Tab value="a">Onglet A</Tab>
        <Tab value="b">Onglet B</Tab>
      </TabList>
      <TabPanel value="a">Panneau A</TabPanel>
      <TabPanel value="b">Panneau B</TabPanel>
    </Tabs>
  )
}

describe('Tabs', () => {
  it('expose les rôles ARIA tablist/tab/tabpanel', () => {
    render(<Fixture />)
    expect(screen.getByRole('tablist')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(2)
  })

  it("affiche le panneau de l'onglet actif par défaut, cache l'autre", () => {
    render(<Fixture />)
    expect(screen.getByText('Panneau A')).toBeInTheDocument()
    expect(screen.queryByText('Panneau B')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Onglet A' })).toHaveAttribute('aria-selected', 'true')
  })

  it('change de panneau au clic', async () => {
    const user = userEvent.setup()
    render(<Fixture />)
    await user.click(screen.getByRole('tab', { name: 'Onglet B' }))
    expect(screen.getByText('Panneau B')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Onglet B' })).toHaveAttribute('aria-selected', 'true')
  })

  it('navigation clavier : flèche droite active l\'onglet suivant', async () => {
    const user = userEvent.setup()
    render(<Fixture />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Onglet B' })).toHaveAttribute('aria-selected', 'true')
  })
})
