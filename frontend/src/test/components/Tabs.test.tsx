import { describe, it, expect, vi } from 'vitest'
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

function Fixture3() {
  return (
    <Tabs defaultValue="a">
      <TabList>
        <Tab value="a">Onglet A</Tab>
        <Tab value="b">Onglet B</Tab>
        <Tab value="c">Onglet C</Tab>
      </TabList>
      <TabPanel value="a">Panneau A</TabPanel>
      <TabPanel value="b">Panneau B</TabPanel>
      <TabPanel value="c">Panneau C</TabPanel>
    </Tabs>
  )
}

function ControlledFixture({ onValueChange }: { onValueChange: (v: string) => void }) {
  return (
    <Tabs value="a" onValueChange={onValueChange}>
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

  it('le focus DOM suit la sélection clavier (roving tabindex APG)', async () => {
    const user = userEvent.setup()
    render(<Fixture />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()
    await user.keyboard('{ArrowRight}')
    const tabB = screen.getByRole('tab', { name: 'Onglet B' })
    expect(document.activeElement).toBe(tabB)
  })

  it('navigation cyclique : flèche droite sur le dernier onglet revient au premier', async () => {
    const user = userEvent.setup()
    render(<Fixture3 />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()
    // Avance jusqu'au dernier onglet (C) avant de tester le rebouclage.
    await user.keyboard('{ArrowRight}{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Onglet C' })).toHaveAttribute('aria-selected', 'true')

    await user.keyboard('{ArrowRight}')
    expect(tabA).toHaveAttribute('aria-selected', 'true')
    expect(document.activeElement).toBe(tabA)
  })

  it('navigation cyclique : flèche gauche sur le premier onglet va au dernier', async () => {
    const user = userEvent.setup()
    render(<Fixture3 />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()
    await user.keyboard('{ArrowLeft}')
    const tabC = screen.getByRole('tab', { name: 'Onglet C' })
    expect(tabC).toHaveAttribute('aria-selected', 'true')
    expect(document.activeElement).toBe(tabC)
  })

  it('End active le dernier onglet, Home le premier', async () => {
    const user = userEvent.setup()
    render(<Fixture3 />)
    const tabA = screen.getByRole('tab', { name: 'Onglet A' })
    tabA.focus()

    await user.keyboard('{End}')
    const tabC = screen.getByRole('tab', { name: 'Onglet C' })
    expect(tabC).toHaveAttribute('aria-selected', 'true')
    expect(document.activeElement).toBe(tabC)

    await user.keyboard('{Home}')
    expect(screen.getByRole('tab', { name: 'Onglet A' })).toHaveAttribute('aria-selected', 'true')
    expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'Onglet A' }))
  })

  it('mode contrôlé : le clic notifie onValueChange sans muter l\'état interne', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()
    render(<ControlledFixture onValueChange={handleChange} />)

    await user.click(screen.getByRole('tab', { name: 'Onglet B' }))

    expect(handleChange).toHaveBeenCalledWith('b')
    // Mode contrôlé : value="a" reste imposé par le parent tant qu'il ne
    // change pas la prop -> le panneau affiché reste A, pas d'état interne.
    expect(screen.getByText('Panneau A')).toBeInTheDocument()
    expect(screen.queryByText('Panneau B')).not.toBeInTheDocument()
  })

  it('utilise ring-offset-2 au focus (harmonise avec Button)', () => {
    render(
      <Tabs defaultValue="a">
        <TabList>
          <Tab value="a">Onglet A</Tab>
          <Tab value="b">Onglet B</Tab>
        </TabList>
      </Tabs>
    )
    const tab = screen.getByRole('tab', { name: 'Onglet A' })
    expect(tab.className).toMatch(/focus-visible:ring-offset-2/)
    expect(tab.className).not.toMatch(/ring-offset-1/)
  })
})
