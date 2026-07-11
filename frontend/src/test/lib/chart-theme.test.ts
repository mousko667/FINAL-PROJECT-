import { describe, it, expect } from 'vitest'
import {
  SERIES_PALETTE_LIGHT,
  SERIES_PALETTE_DARK,
  getSeriesColor,
} from '@/lib/chart-theme'

const STATUS = ['#3E7C5A', '#B5852A', '#C4622E', '#A6432E', '#2F6690']

describe('chart-theme palette', () => {
  it('les deux modes ont le même nombre de slots (mêmes entités re-stepées)', () => {
    expect(SERIES_PALETTE_LIGHT.length).toBe(SERIES_PALETTE_DARK.length)
    expect(SERIES_PALETTE_LIGHT.length).toBeGreaterThanOrEqual(6)
  })

  it('la palette de séries light n\'empiète pas sur les couleurs de statut', () => {
    for (const c of SERIES_PALETTE_LIGHT) {
      expect(STATUS).not.toContain(c.toUpperCase())
    }
  })

  it('getSeriesColor suit l\'entité par index (ordre fixe, jamais cyclé en hue générée)', () => {
    expect(getSeriesColor(0)).toBe(SERIES_PALETTE_LIGHT[0])
    expect(getSeriesColor(1)).toBe(SERIES_PALETTE_LIGHT[1])
    expect(getSeriesColor(0, true)).toBe(SERIES_PALETTE_DARK[0])
  })

  it('getSeriesColor replie (fold) au-delà de la longueur au lieu de générer une hue', () => {
    const n = SERIES_PALETTE_LIGHT.length
    // au-delà de n : reste dans la palette (dernier slot ou "Other"), pas de undefined
    expect(SERIES_PALETTE_LIGHT).toContain(getSeriesColor(n + 3))
  })
})
