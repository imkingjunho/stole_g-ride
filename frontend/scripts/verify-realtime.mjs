// MSW 없이 HTTP 대역과 STOMP 프로토콜 대역으로 브라우저의 실제 클라이언트를 검사한다.
// 팀 Java 서버·실제 JWT 서명·그룹 멤버 인가·카카오 연결을 검증하는 스크립트는 아니다.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { chromium } = require(
  process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES
    ? `${process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES}/playwright`
    : 'playwright',
);
const root = fileURLToPath(new URL('..', import.meta.url));
const origin = 'http://127.0.0.1:5195';
const user = {
  id: 41,
  email: 'realtime@jnu.ac.kr',
  nickname: '연결검사',
  gender: 'M',
  status: 'ACTIVE',
  department: '인공지능학부',
  grade: 2,
};
let token = '';
let refreshes = 0;
let profileUnavailable = false;
let refreshRejected = false;
let request = null;
let nextId = 100;
let patchBody;
let holdNextProfile = false;
let releaseProfile;
const backend = createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', origin);
  res.setHeader('Access-Control-Allow-Headers', 'content-type,authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PATCH,DELETE,OPTIONS');
  res.setHeader('Content-Type', 'application/json');
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  const success = (data) => res.end(JSON.stringify({ success: true, data, error: null }));
  const fail = (status) => {
    res.writeHead(status);
    res.end(
      JSON.stringify({
        success: false,
        data: null,
        error: {
          code: status === 401 ? 'INVALID_TOKEN' : 'INTERNAL_ERROR',
          message: '검사용 오류',
        },
      }),
    );
  };
  if (req.url === '/api/auth/login')
    return success({
      accessToken: 'expired',
      refreshToken: 'refresh-0',
      user,
      accessTokenExpiresIn: 1,
    });
  if (req.url === '/api/auth/refresh') {
    refreshes++;
    await new Promise((r) => setTimeout(r, 80));
    if (refreshRejected) return fail(401);
    token = `renewed-${refreshes}`;
    return success({
      accessToken: token,
      refreshToken: `refresh-${refreshes}`,
      user,
      accessTokenExpiresIn: 1800,
    });
  }
  if (!token || req.headers.authorization !== `Bearer ${token}`) return fail(401);
  if (req.url === '/api/users/me') {
    if (holdNextProfile) {
      holdNextProfile = false;
      await new Promise((resolve) => {
        releaseProfile = resolve;
      });
    }
    if (profileUnavailable) return fail(503);
    if (req.method === 'PATCH') {
      let body = '';
      for await (const part of req) body += part;
      patchBody = JSON.parse(body);
      Object.assign(user, patchBody);
    }
    return success(user);
  }
  if (req.url === '/api/stats/me')
    return success({ totalRides: 0, totalSaving: 0, averageSavingRate: 0 });
  if (req.url === '/api/hubs') return success([]);
  if (req.url === '/api/requests/me') return success(request);
  if (req.url === '/api/requests' && req.method === 'POST') {
    request = {
      requestId: ++nextId,
      userId: 41,
      hub: { id: 1, name: '전남대 후문', lat: 35.18, lng: 126.9 },
      destName: '광주송정역',
      destLat: 35.13,
      destLng: 126.79,
      departAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      maxWaitMin: 10,
      sameGenderOnly: false,
      maxDetourRatio: 1.3,
      soloDistance: 10591,
      soloFare: 12400,
      status: 'WAITING',
      remainingSeconds: 600,
      candidateCount: 0,
      estimated: true,
      groupId: null,
    };
    return success(request);
  }
  if (req.url?.startsWith('/api/requests/') && req.method === 'DELETE') {
    request = null;
    return success(null);
  }
  return fail(404);
});
await new Promise((r) => backend.listen(5196, '127.0.0.1', r));
const server = spawn(
  process.execPath,
  ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5195', '--strictPort'],
  {
    cwd: root,
    env: { ...process.env, VITE_API_MODE: 'real', VITE_API_BASE_URL: 'http://127.0.0.1:5196' },
  },
);
let browser;
try {
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Vite 시작 시간 초과')), 20000);
    server.stdout.on('data', (b) => {
      if (b.toString().includes('Local:')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    server.on('error', reject);
    server.on('exit', (code) => {
      clearTimeout(timeout);
      reject(new Error(`Vite 종료 ${code}`));
    });
  });
  browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-gl=angle', '--use-angle=swiftshader'],
  });
  const page = await browser.newPage({ viewport: { width: 360, height: 800 } });
  page.setDefaultTimeout(15000);
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const connections = [];
  await page.routeWebSocket('ws://127.0.0.1:5196/ws', (socket) => {
    const peer = { socket, subscriptions: new Map(), connected: false, bearer: '' };
    connections.push(peer);
    socket.onMessage((data) => {
      const frame = data.toString();
      if (/^(CONNECT|STOMP)\n/.test(frame)) {
        peer.bearer = frame.match(/Authorization:([^\n]+)/)?.[1] ?? '';
        assert.equal(peer.bearer, `Bearer ${token}`);
        peer.connected = true;
        socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      } else if (frame.startsWith('SUBSCRIBE\n')) {
        peer.subscriptions.set(
          frame.match(/destination:([^\n]+)/)[1],
          frame.match(/\nid:([^\n]+)/)[1],
        );
      } else if (frame.startsWith('DISCONNECT\n')) {
        const receipt = frame.match(/receipt:([^\n]+)/)?.[1];
        if (receipt) socket.send(`RECEIPT\nreceipt-id:${receipt}\n\n\0`);
        socket.close();
      }
    });
  });
  const currentPeer = () => connections.at(-1);
  const sendStatus = (body) => {
    const peer = currentPeer();
    const id = peer.subscriptions.get('/user/queue/status');
    assert.ok(id, '대기 상태 구독 필요');
    const json = typeof body === 'string' ? body : JSON.stringify(body);
    peer.socket.send(
      `MESSAGE\nsubscription:${id}\nmessage-id:test-${Date.now()}\ndestination:/user/queue/status\n\n${json}\0`,
    );
  };
  // 공개 진입점으로 로그인하고 실제 REST 갱신 경로를 거친다.
  await page.goto(`${origin}/login`);
  await page.getByLabel('학교 이메일').fill(user.email);
  await page.getByLabel('비밀번호').fill('test-password');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL('**/request');
  const waitConnected = async () => {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
      const connected = await page.evaluate(
        async () =>
          (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.getState()
            .status === 'connected',
      );
      if (connected && currentPeer()?.subscriptions.has('/user/queue/status')) return;
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    throw new Error('실시간 연결·구독 시간 초과');
  };
  await waitConnected();
  assert.equal(refreshes, 1);
  assert.equal(currentPeer().bearer, 'Bearer renewed-1');
  console.log('PASS 만료 Access → REST 갱신 → 새 Bearer STOMP CONNECT');

  const createRequest = () =>
    page.evaluate(async () => {
      const { api, unwrap } = await import('/src/shared/api/client.ts');
      const response = await api.POST('/api/requests', {
        body: {
          hubId: 1,
          destName: '광주송정역',
          destLat: 35.13,
          destLng: 126.79,
          maxWaitMin: 10,
          sameGenderOnly: false,
          maxDetourRatio: 1.3,
        },
      });
      return unwrap(response.data, response.error);
    });
  await createRequest();
  await page.getByRole('link', { name: '대기', exact: true }).click();
  await page.getByRole('heading', { name: '함께 갈 사람을 찾고 있어요' }).waitFor();
  await page.waitForFunction(() => document.body.textContent.includes('0명'));
  sendStatus({
    requestId: request.requestId,
    status: 'WAITING',
    remainingSeconds: 485,
    candidateCount: 7,
  });
  await page.getByText('7명', { exact: true }).waitFor();
  // 잘못된/다른 요청의 알림은 화면과 연결 상태를 바꾸지 않는다.
  sendStatus('{broken');
  sendStatus({
    requestId: request.requestId,
    status: 'WAITING',
    remainingSeconds: -1,
    candidateCount: 99,
  });
  sendStatus({
    requestId: request.requestId + 1,
    status: 'WAITING',
    remainingSeconds: 300,
    candidateCount: 99,
  });
  await page.waitForTimeout(150);
  assert.equal(await page.getByText('99명', { exact: true }).count(), 0);
  assert.equal(
    await page.evaluate(
      async () =>
        (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.getState().status,
    ),
    'connected',
  );
  console.log('PASS 대기 push 반영·잘못된 JSON/음수/다른 요청 알림 무시');

  const firstId = request.requestId;
  await page.getByRole('button', { name: '요청 취소', exact: true }).click();
  await page.waitForURL('**/request');
  assert.equal(request, null);
  await createRequest();
  await page.getByRole('link', { name: '대기', exact: true }).click();
  await page.getByText('0명', { exact: true }).waitFor();
  sendStatus({ requestId: firstId, status: 'WAITING', remainingSeconds: 100, candidateCount: 99 });
  await page.waitForTimeout(150);
  assert.equal(await page.getByText('99명', { exact: true }).count(), 0);
  console.log('PASS 취소·새 요청 뒤 이전 요청 push 무시');

  request.candidateCount = 4;
  token = ''; // 다음 REST에서 Access 만료를 재현한다.
  const beforeReconnect = connections.length;
  currentPeer().socket.close({ code: 1001, reason: '검사용 연결 끊김' });
  await page.getByRole('status').filter({ hasText: '실시간 연결이 끊겼어요' }).waitFor();
  await page.getByText('4명', { exact: true }).waitFor();
  await waitConnected();
  assert.ok(connections.length > beforeReconnect);
  assert.equal(refreshes, 2, 'REST 폴링과 재접속이 갱신을 공유한다');
  assert.equal(currentPeer().bearer, 'Bearer renewed-2');
  console.log('PASS 단절 표시·재접속 새 Bearer·구독 복구·놓친 대기 상세 재조회');

  // HTTP 사전 검사가 실패해도 beforeConnect의 거부된 Promise로 멈추지 않는다.
  profileUnavailable = true;
  currentPeer().socket.close({ code: 1001 });
  await page.waitForTimeout(3600);
  assert.equal(
    await page.evaluate(
      async () =>
        (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.getState().status,
    ),
    'disconnected',
  );
  profileUnavailable = false;
  await waitConnected();
  console.log('PASS 재접속 사전 HTTP 장애 후 재시도·복구');

  request.status = 'EXPIRED';
  request.remainingSeconds = 0;
  sendStatus({
    requestId: request.requestId,
    status: 'EXPIRED',
    remainingSeconds: 0,
    candidateCount: 0,
  });
  await page.getByRole('heading', { name: '대기 시간이 끝났어요' }).waitFor();
  console.log('PASS 만료 push → 상세 조회 → 만료 화면');

  await page.getByRole('link', { name: '내 정보', exact: true }).click();
  await page.getByRole('button', { name: '프로필 수정', exact: true }).click();
  await page.getByLabel('닉네임').fill('새닉네임');
  await page.getByLabel('학과', { exact: true }).fill('');
  await page.getByLabel('학년', { exact: true }).fill('');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await page.getByRole('heading', { name: '새닉네임', exact: true }).waitFor();
  assert.deepEqual(patchBody, { nickname: '새닉네임', department: null, grade: null });
  assert.equal(
    await page.evaluate(
      async () =>
        (await import('/src/shared/stores/authStore.ts')).useAuthStore.getState().user.nickname,
    ),
    '새닉네임',
  );
  console.log('PASS 프로필 PATCH 전체 입력·선택값 삭제·인증 상태 반영');

  // 사전 인증 응답이 늦게 돌아와도 이미 로그아웃한 연결을 복구하면 안 된다.
  holdNextProfile = true;
  currentPeer().socket.close({ code: 1001 });
  const holdDeadline = Date.now() + 10000;
  while (!releaseProfile && Date.now() < holdDeadline) await new Promise((r) => setTimeout(r, 50));
  assert.ok(releaseProfile, '사전 인증 요청 보류');
  await page.evaluate(async () =>
    (await import('/src/shared/stores/authStore.ts')).useAuthStore.getState().logout(),
  );
  await page.waitForURL('**/login');
  const beforeLateResponse = connections.length;
  releaseProfile();
  await page.waitForTimeout(500);
  assert.equal(connections.length, beforeLateResponse);
  assert.equal(
    await page.evaluate(
      async () =>
        (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.getState().status,
    ),
    'disconnected',
  );
  console.log('PASS 로그아웃 뒤 늦은 사전 인증 응답으로 소켓 복구 방지');
  await page.getByLabel('학교 이메일').fill(user.email);
  await page.getByLabel('비밀번호').fill('test-password');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL('**/request');
  await waitConnected();

  token = '';
  refreshRejected = true;
  currentPeer().socket.close({ code: 1001 });
  await page.waitForURL('**/login');
  const connectionCount = connections.length;
  await page.waitForTimeout(3500);
  assert.equal(connections.length, connectionCount, '갱신 거절 뒤 재접속 중단');
  assert.equal(
    await page.evaluate(
      async () =>
        (await import('/src/shared/stores/authStore.ts')).useAuthStore.getState().accessToken,
    ),
    null,
  );
  assert.equal(
    await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length),
    0,
  );
  assert.deepEqual(errors, []);
  console.log('PASS Refresh 거절 시 로그아웃·재접속 중단·MSW 없음·페이지 오류 0');
} finally {
  await browser?.close();
  server.kill();
  backend.closeAllConnections();
  await new Promise((resolve) => backend.close(resolve));
}
