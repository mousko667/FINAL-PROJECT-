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
        border: "var(--border)",
        input: "var(--input)",
        ring: "var(--ring)",
        background: "var(--background)",
        foreground: "var(--foreground)",
        primary: {
          DEFAULT: "var(--primary)",
          foreground: "var(--primary-foreground)",
        },
        secondary: {
          DEFAULT: "var(--secondary)",
          foreground: "var(--secondary-foreground)",
        },
        destructive: {
          DEFAULT: "var(--destructive)",
          foreground: "var(--destructive-foreground)",
        },
        muted: {
          DEFAULT: "var(--muted)",
          foreground: "var(--muted-foreground)",
        },
        accent: {
          DEFAULT: "var(--accent)",
          foreground: "var(--accent-foreground)",
        },
        popover: {
          DEFAULT: "var(--popover)",
          foreground: "var(--popover-foreground)",
        },
        card: {
          DEFAULT: "var(--card)",
          foreground: "var(--card-foreground)",
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
