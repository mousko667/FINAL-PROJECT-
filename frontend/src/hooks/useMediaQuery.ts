import { useEffect, useState } from 'react'

/**
 * Suit une media query CSS depuis React.
 *
 * Introduit pour la dette a11y relevée en V3-A (AUDIT-023) : le tiroir de
 * navigation fermé est masqué hors écran par une simple translation, ce qui ne
 * le retire pas de l'ordre de tabulation. L'attribut `inert` doit donc être posé
 * conditionnellement — et seulement sous le point de rupture `md`, au-dessus
 * duquel la barre latérale est une colonne statique parfaitement navigable.
 * Une media query CSS ne peut pas piloter un attribut : il faut la lire en JS.
 *
 * jsdom ne fournit pas `matchMedia` ; `src/test/setup.ts` installe un shim qui
 * renvoie toujours `matches: false`. Les tests voient donc l'état « écran
 * étroit », cohérent avec l'absence de mise en page réelle sous jsdom.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia?.(query).matches ?? false)

  useEffect(() => {
    const mql = window.matchMedia?.(query)
    if (!mql) return

    setMatches(mql.matches)
    const onChange = (e: MediaQueryListEvent) => setMatches(e.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [query])

  return matches
}
