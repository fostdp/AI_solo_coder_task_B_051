/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        'ochre': '#8B4513',
        'bronze-green': '#4A7C59',
        'alert-red': '#D64545',
        'warning-yellow': '#E8A838',
        'normal-green': '#52B788',
        'dark': {
          100: '#1a1a2e',
          200: '#16213e',
          300: '#0f3460',
          400: '#1a1a2e',
          500: '#0d1117',
        }
      },
      fontFamily: {
        'serif-sc': ['"Noto Serif SC"', 'serif'],
        'sans-sc': ['"Noto Sans SC"', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
