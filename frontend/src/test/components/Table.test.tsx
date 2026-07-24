import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Table, THead, TBody, TR, TH, TD } from '@/components/ui/Table'

describe('Table', () => {
  it('rend une table sémantique avec en-tête et lignes', () => {
    render(
      <Table>
        <THead>
          <TR><TH>Référence</TH><TH>Montant</TH></TR>
        </THead>
        <TBody>
          <TR><TD>F-001</TD><TD>1 000 XAF</TD></TR>
        </TBody>
      </Table>
    )
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Référence' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'F-001' })).toBeInTheDocument()
  })

  it('enveloppe la table dans un conteneur scrollable horizontalement', () => {
    const { container } = render(<Table><TBody><TR><TD>x</TD></TR></TBody></Table>)
    expect((container.firstElementChild as HTMLElement).className).toMatch(/overflow-x-auto/)
  })

  it('en-tête : encre douce + uppercase ; lignes : séparateur hairline', () => {
    render(
      <Table>
        <THead><TR><TH>Statut</TH></TR></THead>
        <TBody><TR data-testid="row"><TD>x</TD></TR></TBody>
      </Table>
    )
    expect(screen.getByRole('columnheader').className).toMatch(/text-ink-soft/)
    expect(screen.getByRole('columnheader').className).toMatch(/uppercase/)
    expect(screen.getByTestId('row').className).toMatch(/border-hairline/)
  })

  it('fusionne une className extra sur une cellule (ex. .num pour montants)', () => {
    render(<Table><TBody><TR><TD className="num">1 000</TD></TR></TBody></Table>)
    expect(screen.getByRole('cell').className).toMatch(/num/)
  })

  it('applique containerClassName au wrapper overflow et className au <table>', () => {
    const { container } = render(
      <Table className="table-cls" containerClassName="wrapper-cls">
        <TBody><TR><TD>x</TD></TR></TBody>
      </Table>
    )
    const wrapper = container.querySelector('div.wrapper-cls')
    expect(wrapper).not.toBeNull()
    expect(wrapper?.className).toMatch(/overflow-x-auto/)
    const table = container.querySelector('table')
    expect(table?.className).toMatch(/table-cls/)
    expect(wrapper?.className).not.toMatch(/table-cls/)
  })

  // AUDIT-023 : un conteneur qui défile doit être atteignable au clavier, sinon
  // les colonnes hors cadre ne sont accessibles qu'à la souris (axe-core
  // `scrollable-region-focusable`). La primitive porte la correction pour tous
  // les tableaux du design system.
  it('rend le conteneur défilable focalisable au clavier', () => {
    const { container } = render(<Table><TBody><TR><TD>x</TD></TR></TBody></Table>)
    const wrapper = container.firstElementChild as HTMLElement
    expect(wrapper).toHaveAttribute('tabindex', '0')
    expect(wrapper).toHaveAttribute('role', 'region')
  })

  it('nomme la région défilable via containerLabel', () => {
    const { container } = render(
      <Table containerLabel="Factures en attente">
        <TBody><TR><TD>x</TD></TR></TBody>
      </Table>
    )
    expect(container.firstElementChild).toHaveAttribute('aria-label', 'Factures en attente')
  })
})
