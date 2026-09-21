export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: 'var(--brand)',
        ink: 'var(--ink)',
        surface: 'var(--surface)',
        canvas: 'var(--canvas)',
      },
      fontFamily: { sans: ['system-ui', 'Apple SD Gothic Neo', 'Malgun Gothic', 'sans-serif'] },
      borderRadius: { card: 'var(--radius-card)' },
    },
  },
  plugins: [],
};
