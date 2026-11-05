import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';

// 개발 서버는 5173 고정. 백엔드 CorsConfig·WebSocketConfig 가 이 포트만 허용한다.
export default defineConfig({
  plugins: [react()],
  resolve: {
    // tsconfig.app.json 의 paths 와 같은 별칭. 둘 중 하나만 바꾸면 어긋난다
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
