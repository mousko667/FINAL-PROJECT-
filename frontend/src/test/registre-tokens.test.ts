import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

// Source-level test for the Registre design-system tokens (Track B / Lot B1).
//
// Why source-level instead of getComputedStyle: vitest runs in jsdom with no
// CSS pipeline, so index.css is never loaded/compiled (Tailwind/PostCSS do not
// run) — a getComputedStyle assertion on a `bg-ground` class would read empty
// values, and injecting a <style> tag to make it pass would be tautological
// (it tests the injected snippet, not the real stylesheet). Instead we read
// src/index.css as text and assert directly against the CSS source: every key
// token must be defined in both :root and .dark, and must not be a copy-paste
// no-op between the two (i.e. .dark must actually remap at least --ground).

const CSS_PATH = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../index.css')

const KEY_TOKENS = ['--ground', '--hairline', '--ink', '--pos', '--warn', '--hot', '--crit', '--info']

let cssSource: string
let rootBlock: string
let darkBlock: string

function extractBlock(source: string, selectorPattern: RegExp): string {
  const match = selectorPattern.exec(source)
  if (!match) {
    throw new Error(`Could not locate block for pattern ${selectorPattern}`)
  }
  const openBraceIndex = match.index + match[0].length - 1
  let depth = 0
  for (let i = openBraceIndex; i < source.length; i++) {
    if (source[i] === '{') depth++
    if (source[i] === '}') {
      depth--
      if (depth === 0) {
        return source.slice(openBraceIndex + 1, i)
      }
    }
  }
  throw new Error('Unbalanced braces while extracting block')
}

function tokenValue(block: string, token: string): string | null {
  // Matches "--token: <value>;" while avoiding partial matches like --hairline-strong
  // when looking up --hairline.
  const re = new RegExp(`(?:^|[\\s;{])${token}\\s*:\\s*([^;]+);`, 'm')
  const match = re.exec(block)
  return match ? match[1].trim() : null
}

beforeAll(() => {
  cssSource = readFileSync(CSS_PATH, 'utf-8')
  rootBlock = extractBlock(cssSource, /:root\s*\{/)
  darkBlock = extractBlock(cssSource, /\.dark\s*\{/)
})

describe('Registre design-system tokens (src/index.css)', () => {
  it('defines every key token in :root', () => {
    for (const token of KEY_TOKENS) {
      const value = tokenValue(rootBlock, token)
      expect(value, `expected ${token} to be defined in :root`).toBeTruthy()
    }
  })

  it('re-defines every key token in .dark', () => {
    for (const token of KEY_TOKENS) {
      const value = tokenValue(darkBlock, token)
      expect(value, `expected ${token} to be re-defined in .dark`).toBeTruthy()
    }
  })

  it('remaps --ground to a different value in .dark (not a no-op copy)', () => {
    const light = tokenValue(rootBlock, '--ground')
    const dark = tokenValue(darkBlock, '--ground')
    expect(light).toBeTruthy()
    expect(dark).toBeTruthy()
    expect(dark).not.toBe(light)
  })

  it('remaps --ink to a different value in .dark (proves a designed palette, not just ground)', () => {
    const light = tokenValue(rootBlock, '--ink')
    const dark = tokenValue(darkBlock, '--ink')
    expect(light).toBeTruthy()
    expect(dark).toBeTruthy()
    expect(dark).not.toBe(light)
  })

  it('remaps --pos to a different value in .dark (semantic states are redesigned, not inverted mechanically)', () => {
    const light = tokenValue(rootBlock, '--pos')
    const dark = tokenValue(darkBlock, '--pos')
    expect(light).toBeTruthy()
    expect(dark).toBeTruthy()
    expect(dark).not.toBe(light)
  })

  it('keeps --primary and --primary-foreground untouched (single color system, not replaced)', () => {
    expect(tokenValue(rootBlock, '--primary')).toBe('213 64% 16%')
    expect(tokenValue(rootBlock, '--primary-foreground')).toBeTruthy()
  })

  it('defines the .num tabular-numerals utility', () => {
    expect(cssSource).toMatch(/\.num\s*\{[^}]*font-variant-numeric:\s*tabular-nums/)
  })
})
