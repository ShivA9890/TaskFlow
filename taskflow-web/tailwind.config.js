/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: '#F5F6F8',
        surface: '#FFFFFF',
        line: '#E2E5EA',
        ink: '#15171C',
        slate: '#5C6472',
        muted: '#8A919E',
        accent: '#3E4BD8',
        accentInk: '#232A9E',
        accentWash: '#EEEFFC',
        sevLow: '#5C6472',
        sevMedium: '#0E7490',
        sevHigh: '#B45309',
        sevCritical: '#B91C1C',
      },
      fontFamily: {
        sans: ['"Inter Tight"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'monospace'],
      },
      letterSpacing: { tightest: '-0.02em' },
    },
  },
  plugins: [],
}