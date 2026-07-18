/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: [
    './pages/**/*.{ts,tsx}',
    './components/**/*.{ts,tsx}',
    './app/**/*.{ts,tsx}',
    './src/**/*.{ts,tsx}',
  ],
  prefix: "",
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        /* OCT Brand palette — usable as bg-oct-navy, text-oct-gold, etc. */
        oct: {
          navy:       "#0F2540",
          "navy-light": "#1A3A5C",
          "navy-dark":  "#091829",
          gold:       "#C8A84B",
          "gold-light": "#D4BC74",
          "gold-muted": "#8A7535",
        },
        /* Registre design-system tokens (Track B / Lot B1) — warm neutrals +
         * semantic states, remapped per-theme via :root / .dark in index.css. */
        ground: "hsl(var(--ground))",
        surface: "hsl(var(--surface))",
        hairline: "hsl(var(--hairline))",
        "hairline-strong": "hsl(var(--hairline-strong))",
        ink: {
          DEFAULT: "hsl(var(--ink))",
          soft: "hsl(var(--ink-soft))",
          faint: "hsl(var(--ink-faint))",
        },
        pos: {
          DEFAULT: "hsl(var(--pos))",
          bg: "hsl(var(--pos-bg))",
        },
        warn: {
          DEFAULT: "hsl(var(--warn))",
          bg: "hsl(var(--warn-bg))",
        },
        hot: {
          DEFAULT: "hsl(var(--hot))",
          bg: "hsl(var(--hot-bg))",
        },
        crit: {
          DEFAULT: "hsl(var(--crit))",
          bg: "hsl(var(--crit-bg))",
        },
        info: {
          DEFAULT: "hsl(var(--info))",
          bg: "hsl(var(--info-bg))",
        },
        "gold-deep": "hsl(var(--gold-deep))",
        "header-grad-from": "var(--header-grad-from)",
        "header-grad-to": "var(--header-grad-to)",
        "header-accent": "var(--header-accent)",
        "nav-tint": "var(--nav-tint)",
        "page-tint": "var(--page-tint)",
        "kpi-info": "var(--kpi-info-bg)",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}
