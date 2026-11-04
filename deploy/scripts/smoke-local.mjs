import { pathToFileURL } from 'node:url';

/** 테스트 전용 계정으로 로컬 REST 흐름을 검사한다. 비밀번호·토큰은 출력하지 않는다. */
export function readConfig(env = process.env) {
  const url = new URL(env.SMOKE_BASE_URL || 'http://127.0.0.1:8080');
  if (
    url.protocol !== 'http:' ||
    !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) ||
    url.username || url.password || url.pathname !== '/' || url.search || url.hash
  ) {
    throw new Error('SMOKE_BASE_URL은 경로 없는 로컬 HTTP 주소만 허용합니다.');
  }
  if (!env.SMOKE_EMAIL?.trim() || !env.SMOKE_PASSWORD) {
    throw new Error('테스트 전용 계정의 SMOKE_EMAIL·SMOKE_PASSWORD를 환경변수로 지정하세요.');
  }
  return { baseUrl: url.origin, email: env.SMOKE_EMAIL.trim(), password: env.SMOKE_PASSWORD };
}

export async function runSmoke(config, { fetchImpl = fetch, log = console.log, now = Date.now } = {}) {
  // import해서 호출할 때도 주소·자격증명 검사를 우회하지 않는다.
  const { baseUrl, email, password } = readConfig({
    SMOKE_BASE_URL: config.baseUrl, SMOKE_EMAIL: config.email, SMOKE_PASSWORD: config.password,
  });
  let token;
  let requestId;
  const pendingIds = new Set();
  let signedIn = false;
  let passed = 0;
  const positiveId = (id) => Number.isSafeInteger(id) && id > 0;
  const success = (response, status = 200) =>
    response.status === status && response.body.success === true && response.body.error === null;
  function check(condition, label) {
    if (!condition) throw new Error(`${label}: 기대한 상태·응답과 다릅니다.`);
    passed++;
    log(`통과: ${label}`);
  }
  async function call(method, path, body, authenticated = true) {
    let response;
    try {
      response = await fetchImpl(`${baseUrl}${path}`, {
        method,
        headers: {
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(authenticated && token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: 'error',
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      throw new Error(`${method} ${path}: 연결 실패·시간 초과·리다이렉트 발생`);
    }
    let envelope;
    try { envelope = await response.json(); } catch {
      throw new Error(`${method} ${path}: JSON 응답이 아닙니다.`);
    }
    if (!envelope || typeof envelope.success !== 'boolean' ||
        !Object.hasOwn(envelope, 'data') || !Object.hasOwn(envelope, 'error')) {
      throw new Error(`${method} ${path}: ApiResponse 형식이 아닙니다.`);
    }
    return { status: response.status, body: envelope };
  }

  let failure;
  try {
    const anonymous = await call('GET', '/api/requests/me', undefined, false);
    check(anonymous.status === 401 && anonymous.body.error?.code === 'UNAUTHORIZED', '미인증 요청 401');
    const login = await call('POST', '/api/auth/login', { email, password }, false);
    const account = login.body.data;
    // 응답 뒤의 다른 검사가 실패해도 발급된 세션은 finally에서 정리한다.
    if (success(login) && typeof account?.accessToken === 'string' && account.accessToken) {
      token = account.accessToken;
      signedIn = true;
    }
    check(signedIn && positiveId(account?.user?.id) && account.user.status === 'ACTIVE' &&
      typeof account.refreshToken === 'string' && account.refreshToken.length > 0, 'JWT 로그인');

    const existing = await call('GET', '/api/requests/me');
    if (!success(existing) || existing.body.data !== null) {
      throw new Error('진행 요청 사전 확인 실패: 기존 요청을 변경하지 않고 중단합니다.');
    }
    check(true, '기존 진행 요청 없음');
    const hubs = await call('GET', '/api/hubs');
    const hub = Array.isArray(hubs.body.data) && hubs.body.data.find((item) =>
      positiveId(item.id) && Number.isFinite(item.lat) && Number.isFinite(item.lng) &&
      item.lat >= 33 && item.lat <= 39 && item.lng >= 124 && item.lng <= 132);
    check(success(hubs) && Boolean(hub), '실제 거점 조회');
    const command = {
      hubId: hub.id, destName: '로컬 통합 검사 목적지',
      destLat: hub.lat, destLng: hub.lng - 0.03,
      // 서버 계약: 한국 시각, 오프셋 없는 ISO 문자열. UTC의 Z를 그대로 보내지 않는다.
      departAt: new Date(now() + 60_000 + 9 * 3_600_000).toISOString().slice(0, 19),
      maxWaitMin: 10, sameGenderOnly: false, maxDetourRatio: 0.30,
    };
    const created = await call('POST', '/api/requests', command);
    if (created.status === 201 && positiveId(created.body.data?.requestId) &&
        created.body.data.userId === account.user.id) {
      requestId = created.body.data.requestId;
      pendingIds.add(requestId);
    }
    check(success(created, 201) && Boolean(requestId) && created.body.data.status === 'WAITING', '요청 생성 201');
    const duplicate = await call('POST', '/api/requests', command);
    // 중복 제한 자체가 고장 나 두 번째 생성이 성공한 경우도 이 실행의 데이터다.
    if (duplicate.status === 201 && positiveId(duplicate.body.data?.requestId) &&
        duplicate.body.data.userId === account.user.id) pendingIds.add(duplicate.body.data.requestId);
    check(duplicate.status === 409 && duplicate.body.error?.code === 'ALREADY_IN_QUEUE', '중복 요청 409');
    const mine = await call('GET', '/api/requests/me');
    check(success(mine) && mine.body.data?.requestId === requestId &&
      mine.body.data.userId === account.user.id && mine.body.data.status === 'WAITING', '생성한 요청 조회');
    const cancelled = await call('DELETE', `/api/requests/${requestId}`);
    check(success(cancelled), '생성한 요청 취소');
    pendingIds.delete(requestId);
    requestId = undefined;
    const empty = await call('GET', '/api/requests/me');
    check(success(empty) && empty.body.data === null, '취소 후 진행 요청 없음');

    const refresh = await call('POST', '/api/auth/refresh', { refreshToken: account.refreshToken }, false);
    const rotated = refresh.body.data;
    check(success(refresh) && rotated?.user?.id === account.user.id &&
      typeof rotated.accessToken === 'string' && rotated.accessToken.length > 0 &&
      typeof rotated.refreshToken === 'string' && rotated.refreshToken.length > 0 &&
      rotated.refreshToken !== account.refreshToken, 'Refresh 토큰 회전');
    token = rotated.accessToken;
    const replay = await call('POST', '/api/auth/refresh', { refreshToken: account.refreshToken }, false);
    check(replay.status === 401 && replay.body.error?.code === 'INVALID_TOKEN', '이전 Refresh 재사용 거절');
    const logout = await call('POST', '/api/auth/logout');
    check(success(logout), '로그아웃');
    signedIn = false;
    const revoked = await call('POST', '/api/auth/refresh', { refreshToken: rotated.refreshToken }, false);
    check(revoked.status === 401 && revoked.body.error?.code === 'INVALID_TOKEN', '로그아웃 후 Refresh 거절');
  } catch (error) {
    failure = error;
  } finally {
    const cleanupFailures = [];
    // 기존 요청이나 조회 응답의 다른 ID는 절대로 삭제하지 않는다.
    for (const pendingId of pendingIds) {
      try {
        if (!success(await call('DELETE', `/api/requests/${pendingId}`))) throw new Error();
        log(`정리: 이번 실행의 요청 ${pendingId} 취소`);
      } catch {
        cleanupFailures.push(`이번 실행의 요청 ${pendingId} 정리 실패 — 상태를 직접 확인하세요`);
      }
    }
    if (signedIn) {
      try {
        if (!success(await call('POST', '/api/auth/logout'))) throw new Error();
      } catch { cleanupFailures.push('테스트 계정 로그아웃 실패'); }
    }
    if (cleanupFailures.length) {
      failure = new Error([failure?.message, ...cleanupFailures].filter(Boolean).join('; '));
    }
  }
  if (failure) throw failure;
  return { passed };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { passed } = await runSmoke(readConfig());
    console.log(`로컬 통합 검사 ${passed}개 통과`);
  } catch (error) {
    console.error(`실패: ${error.message}`);
    process.exitCode = 1;
  }
}
