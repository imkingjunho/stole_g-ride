import createClient from 'openapi-fetch';
import type { paths } from '@/generated/api';
import { useAuthStore } from '@/shared/stores/authStore';

/** `mock` 이면 MSW 가 네트워크를 가로챈다. 코드는 두 모드에서 똑같이 동작한다. */
export const isMockMode = import.meta.env.VITE_API_MODE !== 'real';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * 타입이 붙은 REST 클라이언트.
 *
 * 타입은 `docs/api-spec.yaml` 에서 `npm run gen:api` 로 만든다.
 * **타입을 손으로 정의하지 않는다** — 스펙이 바뀌면 전부 어긋난다 (CLAUDE.md §5).
 *
 * Phase 0 에는 토큰 헤더만 붙인다. 401 자동 갱신(E-09)은 오승원이 T1-2 에서 넣는다.
 */
export const api = createClient<paths>({
  baseUrl,
  headers: { 'Content-Type': 'application/json' },
});

api.use({
  onRequest({ request }) {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    // Phase 0 백엔드는 인증이 꺼져 있고 이 헤더로 사용자를 고른다.
    // 임승현이 실제 인증을 붙이면 이 부분은 지운다.
    if (!token) {
      request.headers.set('X-Dev-User', '1');
    }
    return request;
  },
});

/** 백엔드 공통 응답 껍데기 (PRD §8). 성공이면 data, 실패면 error 가 채워진다. */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
}

/**
 * 껍데기를 벗겨 data 만 돌려준다. 실패면 예외를 던진다.
 *
 * TanStack Query 의 queryFn 안에서 쓰면 에러가 자동으로 error 상태로 넘어간다.
 */
export function unwrap<T>(envelope: ApiEnvelope<T> | undefined): T {
  if (!envelope || !envelope.success || envelope.data === null) {
    const code = envelope?.error?.code ?? 'INTERNAL_ERROR';
    const message = envelope?.error?.message ?? '알 수 없는 오류가 발생했습니다.';
    throw new ApiError(code, message);
  }
  return envelope.data;
}

/** `error.code` 로 분기하기 위한 예외. HTTP 상태만 보고 판단하지 않는다. */
export class ApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}
