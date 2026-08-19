/** @type {import('tailwindcss').Config} */
//
// Bridges src/global.css to NativeWind. Two jobs:
//
//  1. Resolve `font-sans` — which global.css applies to `*` — to Rubik.
//  2. Map the HSL custom properties global.css declares onto Tailwind colour
//     names, so `bg-background` / `text-foreground` mean something.
//
// Font families are named per WEIGHT rather than relying on `font-medium` and
// friends. React Native does not synthesise weights for a custom family: with
// `fontFamily: 'Rubik_400Regular'` a `fontWeight: 600` is either ignored (iOS)
// or faux-bolded (Android). One family per weight is the only way to get real
// Rubik at every weight, so the utilities are namespaced `font-rubik-*` to
// avoid colliding with Tailwind's own font-WEIGHT utilities of the same name.
module.exports = {
  darkMode: 'class',
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Rubik_400Regular'],
        rubik: ['Rubik_400Regular'],
        'rubik-light': ['Rubik_300Light'],
        'rubik-medium': ['Rubik_500Medium'],
        'rubik-semibold': ['Rubik_600SemiBold'],
        'rubik-bold': ['Rubik_700Bold'],
        'rubik-extrabold': ['Rubik_800ExtraBold'],
        'rubik-black': ['Rubik_900Black'],
      },
      colors: {
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        destructive: 'hsl(var(--destructive))',
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        brand: {
          DEFAULT: 'hsl(var(--brand))',
          deep: 'hsl(var(--brand-deep))',
          foreground: 'hsl(var(--brand-foreground))',
        },
        /* The orb's halo rings. */
        halo: {
          DEFAULT: 'hsl(var(--halo))',
          soft: 'hsl(var(--halo-soft))',
        },
        /* Tinted icon tiles and the "Private by design" pill. */
        tile: {
          DEFAULT: 'hsl(var(--tile))',
          foreground: 'hsl(var(--tile-foreground))',
        },
        ok: 'hsl(var(--ok))',
        /* An informational hint. Not the safety gate's amber. */
        notice: {
          DEFAULT: 'hsl(var(--notice))',
          foreground: 'hsl(var(--notice-foreground))',
        },
        chart: {
          1: 'hsl(var(--chart-1))',
          2: 'hsl(var(--chart-2))',
          3: 'hsl(var(--chart-3))',
          4: 'hsl(var(--chart-4))',
          5: 'hsl(var(--chart-5))',
        },
      },
      borderRadius: {
        // `rounded-lg` is the token — 0.875rem / 14px, the radius the cards and
        // fields use. Deliberately no `calc()` variants: NativeWind does not
        // evaluate calc() on native, so `calc(var(--radius) - 2px)` would reach
        // React Native as an unparseable string and the corner would silently
        // come out square. md/sm stay on Tailwind's own static values.
        lg: 'var(--radius)',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
};
