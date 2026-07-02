/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // City-of-LA-leaning palette.
        brand: {
          50: '#eef4fb', 100: '#d6e4f5', 500: '#1d4e89', 600: '#173f6f', 700: '#122f54',
        },
      },
    },
  },
  plugins: [],
};
