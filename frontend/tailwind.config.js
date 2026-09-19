/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // 디자인 토큰은 오승원이 T1-1 에서 채운다. 여기는 뼈대만 둔다.
      minHeight: {
        // 터치 대상 최소 크기 (PRD §10 접근성)
        touch: '44px',
      },
      minWidth: {
        touch: '44px',
      },
    },
  },
  plugins: [],
};
