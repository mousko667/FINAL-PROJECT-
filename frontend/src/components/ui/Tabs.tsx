import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Tabs maison (Lot 1 / design-system) — pas de Radix. role=tablist/tab/tabpanel,
 * aria-selected, navigation clavier (←/→, Home/End). Contrôlé (value) ou
 * non-contrôlé (defaultValue). Onglet actif = fond surface + sous-ligne or
 * (Lot couleur « Soutenu »). Aucun texte en dur.
 */
interface TabsContextValue {
  value: string
  setValue: (v: string) => void
  register: (v: string) => void
  order: React.MutableRefObject<string[]>
  tabNodes: React.MutableRefObject<Map<string, HTMLButtonElement>>
}
const TabsContext = React.createContext<TabsContextValue | null>(null)
function useTabs() {
  const ctx = React.useContext(TabsContext)
  if (!ctx) throw new Error('Les sous-composants Tabs doivent être utilisés dans <Tabs>')
  return ctx
}

export interface TabsProps {
  value?: string
  defaultValue?: string
  onValueChange?: (v: string) => void
  children: React.ReactNode
  className?: string
}

export function Tabs({ value, defaultValue, onValueChange, children, className }: TabsProps) {
  const [internal, setInternal] = React.useState(defaultValue ?? '')
  const current = value ?? internal
  const order = React.useRef<string[]>([])
  const tabNodes = React.useRef<Map<string, HTMLButtonElement>>(new Map())

  const setValue = React.useCallback(
    (v: string) => {
      if (value === undefined) setInternal(v)
      onValueChange?.(v)
    },
    [value, onValueChange]
  )
  const register = React.useCallback((v: string) => {
    if (!order.current.includes(v)) order.current.push(v)
  }, [])

  return (
    <TabsContext.Provider value={{ value: current, setValue, register, order, tabNodes }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

export function TabList({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const { value, setValue, order, tabNodes } = useTabs()
  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const list = order.current
    const i = list.indexOf(value)
    if (i < 0) return
    let next: number | null = null
    if (e.key === 'ArrowRight') next = (i + 1) % list.length
    else if (e.key === 'ArrowLeft') next = (i - 1 + list.length) % list.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = list.length - 1
    if (next !== null) {
      e.preventDefault()
      const nextValue = list[next]
      setValue(nextValue)
      // Le focus DOM doit suivre la sélection clavier (WAI-ARIA APG Tabs,
      // roving tabindex). L'élément bouton existe déjà dans le DOM ; seul son
      // tabIndex sera mis à jour au prochain rendu — .focus() fonctionne
      // indépendamment de ce re-render.
      tabNodes.current.get(nextValue)?.focus()
    }
  }
  return (
    <div
      role="tablist"
      onKeyDown={onKeyDown}
      className={cn(
        'flex items-center gap-1 rounded-t-[4px] bg-nav-tint px-1 pt-1 border-b border-hairline',
        className
      )}
      {...props}
    />
  )
}

export interface TabProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  value: string
}
export function Tab({ value: tabValue, className, ...props }: TabProps) {
  const { value, setValue, register, tabNodes } = useTabs()
  React.useEffect(() => register(tabValue), [register, tabValue])
  const setNode = React.useCallback(
    (el: HTMLButtonElement | null) => {
      if (el) tabNodes.current.set(tabValue, el)
      else tabNodes.current.delete(tabValue)
    },
    [tabNodes, tabValue]
  )
  const active = value === tabValue
  return (
    <button
      ref={setNode}
      role="tab"
      type="button"
      aria-selected={active}
      tabIndex={active ? 0 : -1}
      onClick={() => setValue(tabValue)}
      className={cn(
        'px-4 py-2.5 -mb-px border-b-2 text-sm font-medium transition-colors rounded-t-[4px]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        active
          ? 'bg-surface border-oct-gold text-oct-navy dark:text-info'
          : 'border-transparent text-ink-soft hover:text-ink',
        className
      )}
      {...props}
    />
  )
}

export interface TabPanelProps extends React.HTMLAttributes<HTMLDivElement> {
  value: string
}
export function TabPanel({ value: panelValue, className, children, ...props }: TabPanelProps) {
  const { value } = useTabs()
  if (value !== panelValue) return null
  return (
    <div role="tabpanel" className={className} {...props}>
      {children}
    </div>
  )
}
