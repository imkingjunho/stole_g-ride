export const isMockMode = import.meta.env.VITE_API_MODE !== 'real';

/**
 * 목 모드에서는 같은 출처(상대 경로 `/api/...`)로 요청한다.
 * 배포된 사이트(https://*.vercel.app)가 http://localhost:8080 으로 요청을 보내면, 서비스 워커가
 * 가로채지 못한 순간(강력 새로고침, DevTools "Bypass for network", 서비스 워커 갱신 직후 등)에
 * 사용자 PC 의 localhost 로 새어 나가 "서버에 연결하지 못했어요"(TypeError: Failed to fetch)가 뜬다.
 * 실제 모드에서만 VITE_API_BASE_URL 을 쓴다.
 */
export const apiBaseUrl = isMockMode
  ? ''
  : (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
