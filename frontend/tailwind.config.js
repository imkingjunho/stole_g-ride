/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: '#1769d4', hover: '#1258b5', soft: '#eaf3ff' },
        accent: '#3182f6',
        saving: '#087d4c',
        ink: '#191f28',
        muted: '#6b7684',
        line: '#e5e8eb',
        canvas: '#f2f4f6',
        danger: '#b91c1c',
      },
      fontFamily: { sans: ['Pretendard', 'Apple SD Gothic Neo', 'system-ui', 'sans-serif'] },
      fontSize: {
        caption: ['0.75rem', '1.5'],
        body: ['0.9375rem', '1.6'],
        title: ['1.5rem', '1.3'],
      },
      spacing: { page: '20px' },
      maxWidth: { app: '440px' },
      borderRadius: { card: '20px', control: '14px' },
      boxShadow: { card: '0 4px 24px rgb(25 31 40 / 0.04)' },
      minHeight: { touch: '44px' },
      minWidth: { touch: '44px' },
    },
  },
  plugins: [],
};
