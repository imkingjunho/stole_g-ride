import { HttpResponse, http, delay } from 'msw';
import type { components } from '@/generated/api';
import { apiBaseUrl as BASE } from '@/shared/api/config';
import { mockGroup, mockHubs, mockPlaces, mockRideRequest } from './data';
import { mockState, notifyMatch } from './state';
type S = components['schemas'];
const ok = <T>(data: T) => HttpResponse.json({ success: true, data, error: null });
const fail = (code: string, message: string, status = 400) =>
  HttpResponse.json({ success: false, data: null, error: { code, message } }, { status });
let tokenNumber = 0;
const tokens = (): S['TokenResponse'] => ({
  accessToken: `mock-access-${++tokenNumber}`,
  refreshToken: `mock-refresh-${tokenNumber}`,
  accessTokenExpiresIn: 1800,
  user: mockState.user,
});
const protectedRequest = (request: Request) =>
  request.headers.get('Authorization')?.startsWith('Bearer mock-access-');
export const handlers = [
  http.post(`${BASE}/api/auth/signup`, async ({ request }) => {
    const body = (await request.json()) as S['SignupRequest'];
    const email = body.email?.trim().toLowerCase();
    if (!/^[^@\s]+@jnu\.ac\.kr$/.test(email ?? ''))
      return fail('EMAIL_DOMAIN_NOT_ALLOWED', '학교 이메일을 입력해 주세요.');
    if (mockState.lockedUntil > Date.now())
      return fail('VERIFY_LOCKED', '30분 뒤 다시 시도해 주세요.', 429);
    mockState.signupEmail = email;
    mockState.codeExpires = Date.now() + 600000;
    mockState.codeFailures = 0;
    return ok({ email, expiresInSeconds: 600 } satisfies S['SignupResponse']);
  }),
  http.post(`${BASE}/api/auth/verify`, async ({ request }) => {
    const b = (await request.json()) as S['VerifyRequest'];
    if (mockState.lockedUntil > Date.now()) return fail('VERIFY_LOCKED', '인증이 잠겼어요.', 429);
    if (
      b.email !== mockState.signupEmail ||
      b.code !== '482913' ||
      Date.now() > mockState.codeExpires
    ) {
      mockState.codeFailures++;
      if (mockState.codeFailures >= 5) {
        mockState.lockedUntil = Date.now() + 1800000;
        return fail('VERIFY_LOCKED', '인증이 잠겼어요.', 429);
      }
      return fail('VERIFY_CODE_MISMATCH', '인증 코드를 확인해 주세요.');
    }
    if (
      !b.nickname?.trim() ||
      b.nickname.trim().length < 2 ||
      b.password.length < 8 ||
      new TextEncoder().encode(b.password).length > 72
    )
      return fail('INVALID_INPUT', '입력 값을 확인해 주세요.');
    mockState.user = {
      ...mockState.user,
      email: b.email,
      nickname: b.nickname,
      gender: b.gender,
      department: b.department,
      grade: b.grade,
    };
    mockState.codeExpires = 0;
    return ok(tokens());
  }),
  http.post(`${BASE}/api/auth/login`, async ({ request }) => {
    const b = (await request.json()) as S['LoginRequest'];
    if (!b.email || !b.password || b.password === 'wrong')
      return fail('UNAUTHORIZED', '이메일 또는 비밀번호를 확인해 주세요.', 401);
    return ok(tokens());
  }),
  http.post(`${BASE}/api/auth/refresh`, async ({ request }) => {
    await delay(100);
    const b = (await request.json()) as S['RefreshRequest'];
    return b.refreshToken.startsWith('mock-refresh-')
      ? ok(tokens())
      : fail('UNAUTHORIZED', '로그인이 만료됐어요.', 401);
  }),
  http.post(`${BASE}/api/auth/logout`, () => ok(null)),
  // 인증이 필요한 REST는 무효 토큰에 401을 반환하여 갱신 동작도 확인한다.
  http.all(`${BASE}/api/*`, ({ request }) => {
    if (!protectedRequest(request)) return fail('UNAUTHORIZED', '다시 로그인해 주세요.', 401);
  }),
  http.get(`${BASE}/api/users/me`, () => ok(mockState.user)),
  http.patch(`${BASE}/api/users/me`, async ({ request }) => {
    const b = (await request.json()) as S['UpdateProfileRequest'];
    mockState.user = { ...mockState.user, ...b };
    return ok(mockState.user);
  }),
  http.post(`${BASE}/api/reports`, async ({ request }) => {
    const b = (await request.json()) as S['ReportRequest'];
    if (
      b.groupId !== mockState.group.groupId ||
      !mockState.group.members.some((m) => m.userId === b.reportedUserId && !m.isMe)
    )
      return fail('GROUP_NOT_MEMBER', '신고 대상을 확인해 주세요.', 403);
    return ok(null);
  }),
  http.get(`${BASE}/api/hubs`, () => ok(mockHubs)),
  http.get(`${BASE}/api/places/search`, ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? '';
    return ok(mockPlaces.filter((p) => p.name.includes(q)));
  }),
  http.post(`${BASE}/api/requests`, () =>
    HttpResponse.json({ success: true, data: mockRideRequest, error: null }, { status: 201 }),
  ),
  http.get(`${BASE}/api/requests/me`, () => ok(mockRideRequest)),
  http.delete(`${BASE}/api/requests/:requestId`, () => ok(null)),
  http.get(`${BASE}/api/groups/:groupId/messages`, ({ params }) =>
    Number(params.groupId) === mockState.group.groupId
      ? ok(mockState.messages)
      : fail('GROUP_NOT_FOUND', '동승 정보를 찾을 수 없어요.', 404),
  ),
  http.get(`${BASE}/api/groups/:groupId`, ({ params }) => {
    if (Number(params.groupId) !== mockState.group.groupId)
      return fail('GROUP_NOT_FOUND', '동승 정보를 찾을 수 없어요.', 404);
    return ok(mockState.group);
  }),
  http.post(`${BASE}/api/groups/:groupId/accept`, () => {
    const group = mockState.group;
    if (
      group.status !== 'PENDING' ||
      !group.acceptDeadline ||
      Date.now() > new Date(group.acceptDeadline).getTime()
    )
      return fail('INVALID_INPUT', '수락 시간이 지났어요.');
    group.members.forEach((m) => {
      m.accepted = true;
    });
    group.status = 'CONFIRMED';
    group.acceptDeadline = null;
    notifyMatch('CONFIRMED', '모두 수락했어요. 동승이 확정됐어요.');
    return ok(group);
  }),
  http.post(`${BASE}/api/groups/:groupId/reject`, () => {
    mockState.group.status = 'DISSOLVED';
    notifyMatch('DISSOLVED', '그룹이 해체됐어요. 대기 상태를 확인해 주세요.');
    return ok(null);
  }),
  http.post(`${BASE}/api/groups/:groupId/complete`, () => {
    mockState.group.status = 'COMPLETED';
    mockState.messages = [];
    notifyMatch('COMPLETED', '탑승을 완료했어요.');
    return ok(null);
  }),
  http.get(`${BASE}/api/history`, ({ request }) => {
    const q = new URL(request.url).searchParams;
    const month = q.get('yearMonth');
    const page = Math.max(0, Number(q.get('page') ?? 0));
    const size = Math.min(50, Math.max(1, Number(q.get('size') ?? 20)));
    const data = mockState.history.filter((h) => !month || h.ridedAt.slice(0, 7) === month);
    return ok({
      content: data.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements: data.length,
      totalPages: Math.ceil(data.length / size),
      last: (page + 1) * size >= data.length,
    } satisfies S['HistoryPage']);
  }),
  http.get(`${BASE}/api/stats/me`, () =>
    ok({
      totalRides: mockState.history.length,
      totalSaving: mockState.history.reduce((sum, h) => sum + h.savingAmount, 0),
      averageSavingRate: 0,
      mostUsedHubName: mockGroup.hub.name,
    } satisfies S['MyStats']),
  ),
  http.get(`${BASE}/api/admin/stats`, () => ok({ byHub: [], byHour: [] })),
];
