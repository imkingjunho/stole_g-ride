import createClient from 'openapi-fetch';
import type { paths, components } from '@/generated/api';
import { useAuthStore } from '@/shared/stores/authStore';
import { useToastStore } from '@/shared/stores/toastStore';
import { apiBaseUrl } from './config';
export { isMockMode } from './config';
type Envelope<T> = Pick<components['schemas']['ApiResponse'], 'success' | 'error'> & {
  data?: T | null;
};
const errorMessages: Record<string, string> = {
  VERIFY_LOCKED: '인증 시도 횟수를 초과했어요. 30분 뒤 다시 시도해 주세요.',
  VERIFY_CODE_MISMATCH: '인증 코드가 일치하지 않거나 만료됐어요.',
  EMAIL_DOMAIN_NOT_ALLOWED: '전남대 웹메일(@jnu.ac.kr)을 입력해 주세요.',
  GROUP_NOT_MEMBER: '이 동승 그룹에 접근할 수 없어요.',
  GROUP_NOT_FOUND: '동승 정보를 찾을 수 없어요.',
};
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(errorMessages[code] ?? message);
    this.name = 'ApiError';
  }
}
export function unwrap<T>(
  envelope: Envelope<T> | undefined,
  error?: components['schemas']['ApiErrorResponse'],
): T {
  if (!envelope?.success)
    throw new ApiError(
      error?.error.code ?? envelope?.error?.code ?? 'INTERNAL_ERROR',
      error?.error.message ?? envelope?.error?.message ?? '요청을 처리하지 못했어요.',
    );
  return envelope.data as T;
}
export function showError(error: unknown) {
  useToastStore
    .getState()
    .notify(error instanceof Error ? error.message : '요청을 처리하지 못했어요.', 'error');
}
// 동시에 여러 요청이 만료되어도 refresh 회전은 한 번만 수행한다.
let refreshing: { token: string; promise: Promise<void> } | null = null;
async function refreshSession(token: string) {
  if (refreshing?.token === token) return refreshing.promise;
  const promise = (async () => {
    const response = await fetch(`${apiBaseUrl}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        refreshToken: token,
      } satisfies components['schemas']['RefreshRequest']),
    });
    if (!response.ok) throw new ApiError('UNAUTHORIZED', '로그인을 갱신하지 못했어요.');
    const envelope = (await response.json()) as Envelope<components['schemas']['TokenResponse']>;
    const data = unwrap(envelope);
    if (!data?.accessToken || !data.refreshToken || !data.user)
      throw new ApiError('UNAUTHORIZED', '로그인 응답을 확인할 수 없어요.');
    // 갱신 도중 로그아웃·다른 계정 로그인이 발생했다면 이전 세션을 복구하지 않는다.
    if (useAuthStore.getState().refreshToken === token)
      useAuthStore.setState({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user,
      });
  })();
  refreshing = { token, promise };
  try {
    await promise;
  } finally {
    if (refreshing?.promise === promise) refreshing = null;
  }
}
async function authorizedFetch(input: Request) {
  const original = input.clone();
  const initial = useAuthStore.getState();
  const request = new Request(input);
  if (initial.accessToken) request.headers.set('Authorization', `Bearer ${initial.accessToken}`);
  let response = await fetch(request);
  const path = new URL(request.url).pathname;
  if (response.status !== 401 || path.startsWith('/api/auth/')) return response;
  try {
    const current = useAuthStore.getState();
    if (
      current.sessionVersion !== initial.sessionVersion ||
      !initial.refreshToken ||
      !current.refreshToken
    )
      throw new ApiError('UNAUTHORIZED', '다시 로그인해 주세요.');
    if (current.accessToken === initial.accessToken) await refreshSession(initial.refreshToken);
    const updated = useAuthStore.getState();
    if (
      updated.sessionVersion !== initial.sessionVersion ||
      !updated.accessToken ||
      updated.user?.id !== initial.user?.id
    )
      throw new ApiError('UNAUTHORIZED', '세션이 변경됐어요.');
    original.headers.set('Authorization', `Bearer ${updated.accessToken}`);
    response = await fetch(original);
    if (response.status === 401) throw new ApiError('UNAUTHORIZED', '다시 로그인해 주세요.');
    return response;
  } catch (error) {
    const current = useAuthStore.getState();
    if (
      current.sessionVersion === initial.sessionVersion &&
      current.user?.id === initial.user?.id
    ) {
      current.logout();
      showError(new ApiError('UNAUTHORIZED', '다시 로그인해 주세요.'));
    }
    throw error;
  }
}
export const api = createClient<paths>({ baseUrl: apiBaseUrl, fetch: authorizedFetch });
