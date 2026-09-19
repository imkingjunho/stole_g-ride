import { HttpResponse, http } from 'msw';
import {
  mockGroup,
  mockHistory,
  mockHubs,
  mockMessages,
  mockPlaces,
  mockRideRequest,
  mockStats,
  mockUser,
} from './data';

const BASE = 'http://localhost:8080';

/** 성공 응답 껍데기 (PRD §8). 실패 시에도 세 필드가 모두 나간다. */
function ok<T>(data: T) {
  return HttpResponse.json({ success: true, data, error: null });
}

function fail(code: string, message: string, status: number) {
  return HttpResponse.json({ success: false, data: null, error: { code, message } }, { status });
}

const tokens = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  accessTokenExpiresIn: 1800,
  user: mockUser,
};

/**
 * MSW 핸들러 — `docs/api-spec.yaml` 전체의 happy path (PRD §14.7).
 *
 * 백엔드가 아직 없어도 프론트가 시나리오(§3.2)를 끝까지 클릭할 수 있게 하는 것이 목적이다.
 * **스펙이 바뀌면 여기도 함께 고친다** — 오승원이 유지한다(T1-4).
 */
export const handlers = [
  // ── auth ──────────────────────────────────────────────────
  http.post(`${BASE}/api/auth/signup`, async ({ request }) => {
    const body = (await request.json()) as { email?: string };
    if (!body.email?.endsWith('@jnu.ac.kr')) {
      return fail('EMAIL_DOMAIN_NOT_ALLOWED', '전남대 웹메일 주소로만 가입할 수 있습니다.', 400);
    }
    return ok({ email: body.email, expiresInSeconds: 600 });
  }),
  http.post(`${BASE}/api/auth/verify`, () => ok(tokens)),
  http.post(`${BASE}/api/auth/login`, () => ok(tokens)),
  http.post(`${BASE}/api/auth/refresh`, () => ok(tokens)),
  http.post(`${BASE}/api/auth/logout`, () => ok(null)),

  // ── users ─────────────────────────────────────────────────
  http.get(`${BASE}/api/users/me`, () => ok(mockUser)),
  http.patch(`${BASE}/api/users/me`, () => ok(mockUser)),
  http.post(`${BASE}/api/reports`, () => ok(null)),

  // ── hubs · places ─────────────────────────────────────────
  http.get(`${BASE}/api/hubs`, () => ok(mockHubs)),
  http.get(`${BASE}/api/places/search`, ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? '';
    return ok(mockPlaces.filter((place) => place.name.includes(q) || q.length === 0));
  }),

  // ── requests ──────────────────────────────────────────────
  http.post(`${BASE}/api/requests`, () => HttpResponse.json(
    { success: true, data: mockRideRequest, error: null },
    { status: 201 },
  )),
  http.get(`${BASE}/api/requests/me`, () => ok(mockRideRequest)),
  http.delete(`${BASE}/api/requests/:requestId`, () => ok(null)),

  // ── groups ────────────────────────────────────────────────
  http.get(`${BASE}/api/groups/:groupId/messages`, () => ok(mockMessages)),
  http.get(`${BASE}/api/groups/:groupId`, () => ok(mockGroup)),
  http.post(`${BASE}/api/groups/:groupId/accept`, () => ok(mockGroup)),
  http.post(`${BASE}/api/groups/:groupId/reject`, () => ok(null)),
  http.post(`${BASE}/api/groups/:groupId/complete`, () => ok(null)),

  // ── history · stats ───────────────────────────────────────
  http.get(`${BASE}/api/history`, () => ok(mockHistory)),
  http.get(`${BASE}/api/stats/me`, () => ok(mockStats)),
  http.get(`${BASE}/api/admin/stats`, () => ok({ byHub: [], byHour: [] })),
];
