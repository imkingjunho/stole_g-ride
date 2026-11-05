/// <reference types="vite/client" />

/** .env 로 주입되는 값들. 새 항목을 쓰려면 여기에 먼저 선언한다. */
interface ImportMetaEnv {
  /** `mock` 이면 MSW 가 가짜 서버로 응답한다. `real` 이면 실제 백엔드로 붙는다 */
  readonly VITE_API_MODE: 'mock' | 'real';
  /** 백엔드 주소. 예: http://localhost:8080 */
  readonly VITE_API_BASE_URL: string;
  /** 카카오맵 JS 키. REST 키를 넣지 않는다 (CLAUDE.md §8) */
  readonly VITE_KAKAO_JS_KEY: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
