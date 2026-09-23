export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: 'var(--blue)',
        ink: '#191f28',
        surface: '#ffffff',
        canvas: 'var(--canvas)',
      },
      fontFamily: {
        sans: ['Pretendard', 'system-ui', 'Noto Sans KR', 'Malgun Gothic', 'sans-serif'],
      },
      borderRadius: { card: '18px' },
    },
  },
  plugins: [],
};
