import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readConfig, runSmoke } from './smoke-local.mjs';

const config = { baseUrl: 'http://127.0.0.1:8080', email: 'smoke@jnu.ac.kr', password: 'fixture-secret' };
const reply = (status, data = null, code) => ({
  status, json: async () => ({ success: status < 400, data, error: code ? { code, message: '대역 오류' } : null }),
});
function server(override = () => undefined) {
  const calls = [];
  const logs = [];
  let active = false;
  let created = false;
  let rotated = false;
  let revoked = false;
  const user = { id: 11, status: 'ACTIVE' };
  const tokens = (suffix) => ({ user, accessToken: `access-${suffix}`, refreshToken: `refresh-${suffix}` });
  const fetchImpl = async (url, options) => {
    const call = { path: new URL(url).pathname, ...options };
    calls.push(call);
    assert.equal(call.redirect, 'error');
    assert.ok(call.signal instanceof AbortSignal);
    const alternate = await override(call, { active, created });
    if (alternate) return alternate;
    if (call.path === '/api/auth/login') return reply(200, tokens('old'));
    if (call.path === '/api/auth/refresh') {
      const { refreshToken } = JSON.parse(call.body);
      if (revoked || (rotated && refreshToken === 'refresh-old')) return reply(401, null, 'INVALID_TOKEN');
      rotated = true;
      return reply(200, tokens('new'));
    }
    if (!call.headers.Authorization) return reply(401, null, 'UNAUTHORIZED');
    if (call.path === '/api/auth/logout') { revoked = true; return reply(200); }
    if (call.path === '/api/hubs') return reply(200, [{ id: 2, lat: 35.17, lng: 126.91 }]);
    if (call.path === '/api/requests/me') {
      return reply(200, active ? { requestId: 23, userId: 11, status: 'WAITING' } : null);
    }
    if (call.path === '/api/requests' && call.method === 'POST') {
      if (active) return reply(409, null, 'ALREADY_IN_QUEUE');
      active = true;
      created = true;
      return reply(201, { requestId: 23, userId: 11, status: 'WAITING' });
    }
    if (call.method === 'DELETE') { active = false; return reply(200); }
    throw new Error('예상하지 못한 호출');
  };
  return { calls, logs, fetchImpl, log: (message) => logs.push(message), now: () => Date.UTC(2026, 10, 4, 23, 59) };
}

test('JWT 요청·중복 차단·취소·Refresh 회전·로그아웃을 검증한다', async () => {
  const mock = server();
  assert.deepEqual(await runSmoke(config, mock), { passed: 13 });
  const body = JSON.parse(mock.calls.find((c) => c.path === '/api/requests').body);
  assert.equal(body.departAt, '2026-11-05T09:00:00');
  assert.equal(body.hubId, 2);
  assert.equal(mock.calls.filter((c) => c.method === 'DELETE').length, 1);
  assert.ok(!mock.logs.join('').includes(config.password));
  assert.ok(!mock.logs.join('').includes('access-old'));
});

test('기존 진행 요청이 있으면 생성·취소 없이 중단한다', async () => {
  const mock = server((call) => call.path === '/api/requests/me' && call.headers.Authorization
    ? reply(200, { requestId: 999 }) : undefined);
  await assert.rejects(runSmoke(config, mock), /기존 요청을 변경하지 않고/);
  assert.equal(mock.calls.filter((c) => c.path === '/api/requests' || c.method === 'DELETE').length, 0);
});

test('생성 중 409 경합이 나도 다른 실행의 요청을 취소하지 않는다', async () => {
  const mock = server((call) => call.path === '/api/requests' ? reply(409, null, 'ALREADY_IN_QUEUE') : undefined);
  await assert.rejects(runSmoke(config, mock), /요청 생성 201/);
  assert.equal(mock.calls.filter((c) => c.method === 'DELETE').length, 0);
});

test('조회가 다른 요청 ID를 돌려주면 자신이 생성한 ID만 정리한다', async () => {
  const mock = server((call, state) => call.path === '/api/requests/me' && state.active
    ? reply(200, { requestId: 999, userId: 11, status: 'WAITING' }) : undefined);
  await assert.rejects(runSmoke(config, mock), /생성한 요청 조회/);
  assert.deepEqual(mock.calls.filter((c) => c.method === 'DELETE').map((c) => c.path), ['/api/requests/23']);
});

test('중복 제한이 고장 나 두 건이 생성되면 이번 실행의 두 ID를 모두 정리한다', async () => {
  const mock = server((call, state) => call.path === '/api/requests' && state.active
    ? reply(201, { requestId: 24, userId: 11, status: 'WAITING' }) : undefined);
  await assert.rejects(runSmoke(config, mock), /중복 요청 409/);
  assert.deepEqual(mock.calls.filter((c) => c.method === 'DELETE').map((c) => c.path),
    ['/api/requests/23', '/api/requests/24']);
});

test('요청 생성 뒤 네트워크 오류도 정리하고 응답의 비밀을 출력하지 않는다', async () => {
  const mock = server((call, state) => {
    if (call.path === '/api/requests/me' && state.active) throw new Error('서버 오류 access-old fixture-secret');
  });
  await assert.rejects(runSmoke(config, mock), (error) => {
    assert.match(error.message, /연결 실패/);
    assert.doesNotMatch(error.message, /access-old|fixture-secret/);
    return true;
  });
  assert.equal(mock.calls.find((c) => c.method === 'DELETE').path, '/api/requests/23');
});

test('생성 결과를 받지 못했으면 임의 조회·취소를 하지 않는다', async () => {
  const mock = server((call) => call.path === '/api/requests'
    ? { status: 201, json: async () => { throw new Error('비 JSON'); } } : undefined);
  await assert.rejects(runSmoke(config, mock), /JSON 응답/);
  assert.equal(mock.calls.filter((c) => c.method === 'DELETE').length, 0);
});

test('취소 실패를 성공으로 숨기지 않고 정리할 요청 ID를 알린다', async () => {
  const mock = server((call) => call.method === 'DELETE' ? reply(500, null, 'INTERNAL_ERROR') : undefined);
  await assert.rejects(runSmoke(config, mock), /요청 23 정리 실패/);
});

test('원격 주소·리다이렉트 대상·누락 자격증명을 거부한다', async () => {
  for (const address of ['https://example.com', 'http://127.0.0.1.example.com',
    'http://a:b@localhost', 'http://localhost/api', 'http://localhost/?next=remote']) {
    assert.throws(() => readConfig({ SMOKE_BASE_URL: address, SMOKE_EMAIL: config.email, SMOKE_PASSWORD: config.password }), /로컬 HTTP/);
  }
  assert.throws(() => readConfig({}), /SMOKE_EMAIL/);
  let called = false;
  await assert.rejects(runSmoke({ ...config, baseUrl: 'http://example.com' }, {
    fetchImpl: () => { called = true; },
  }), /로컬 HTTP/);
  assert.equal(called, false);
});
