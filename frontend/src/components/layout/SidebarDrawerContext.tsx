import { createContext, useContext, useCallback, useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Shared open/closed state of the mobile navigation drawer.
 *
 * AUDIT-019: both layouts (staff `AppShell` and `SupplierLayout`) render the same
 * `Header`, and both carried an unconditional `w-64` sidebar. The drawer state lives
 * here so the single hamburger button in `Header` drives whichever sidebar is mounted.
 */
type SidebarDrawerValue = {
  isOpen: boolean
  open: () => void
  close: () => void
  toggle: () => void
}

const SidebarDrawerContext = createContext<SidebarDrawerValue | null>(null)

export function SidebarDrawerProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false)
  const { pathname } = useLocation()

  const open = useCallback(() => setIsOpen(true), [])
  const close = useCallback(() => setIsOpen(false), [])
  const toggle = useCallback(() => setIsOpen((v) => !v), [])

  // Navigating away must close the drawer, otherwise it stays over the new page.
  useEffect(() => {
    setIsOpen(false)
  }, [pathname])

  // Escape closes the drawer — the overlay is not focusable on its own.
  useEffect(() => {
    if (!isOpen) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsOpen(false)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [isOpen])

  return (
    <SidebarDrawerContext.Provider value={{ isOpen, open, close, toggle }}>
      {children}
    </SidebarDrawerContext.Provider>
  )
}

/**
 * Returns the drawer controls. Safe outside a provider (returns a no-op closed
 * state) so `Header` can be rendered in isolation by tests.
 */
export function useSidebarDrawer(): SidebarDrawerValue {
  const ctx = useContext(SidebarDrawerContext)
  if (ctx) return ctx
  return { isOpen: false, open: () => {}, close: () => {}, toggle: () => {} }
}
