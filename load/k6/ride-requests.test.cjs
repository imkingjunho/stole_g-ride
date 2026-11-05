// Node 기본 모듈만 사용한다. k6 HTTP 경계의 실패·정리 동작을 네트워크 없이 검증한다.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const source = fs.readFileSync(path.join(__dirname, 'ride-requests.js'), 'utf8')
  .replace(/^import .*;\n/gm, '')
  .replace('export default function (data)', 'function iteration(data)')
  .replace(/export (const|function) /g, '$1 ')
  + '\nglobalThis.harness = { setup, iteration, handleSummary };';

function response(status, data, malformed = false) {
  return { status, timings: { duration: 12 }, json() {
    if (malformed) throw new Error('bad JSON');
    return { success: status < 400, data };
  } };
}

function load(overrides = {}, env = {}) {
  const calls = [];
  const checks = [];
  let id = 100;
  const http = {
    post(url, body, params) {
      calls.push({ method: 'POST', url, body: JSON.parse(body), params });
      if (url.endsWith('/login')) return response(200, {
        accessToken: `jwt-${++id}`, accessTokenExpiresIn: 1800,
        user: { id, email: JSON.parse(body).email, status: 'ACTIVE' },
      });
      return response(201, { requestId: 23, userId: 101 });
    },
    get(url, params) {
      calls.push({ method: 'GET', url, params });
      if (url.endsWith('/hubs')) return response(200, [{ id: 98, lat: 35.17, lng: 126.9 }]);
      if (params.tags.phase === 'setup') return response(200, null);
      return response(200, { requestId: 23, userId: 101 });
    },
    del(url, body, params) { calls.push({ method: 'DELETE', url, params }); return response(200, null); },
    ...overrides,
  };
  class Metric { add() {} }
  const context = vm.createContext({
    __ENV: { LOAD_TEST_PASSWORD: 'fixture-only', VUS: '3', ...env }, __VU: 1,
    http, sleep() {}, Counter: Metric, Trend: Metric,
    check(value, predicates) {
      const passed = Object.values(predicates).every((predicate) => Boolean(predicate(value)));
      checks.push(passed); return passed;
    },
  });
  vm.runInContext(source, context);
  return { ...context.harness, calls, checks };
}

test('전체 계정 로그인, Bearer 인증과 동적 거점 사용', () => {
  const harness = load();
  const data = harness.setup();
  assert.equal(data.accounts.length, 3);
  assert.equal(harness.calls.filter((call) => call.url.endsWith('/login')).length, 3);
  harness.iteration(data);
  const create = harness.calls.find((call) => call.url.endsWith('/requests') && call.method === 'POST');
  assert.equal(create.body.hubId, 98);
  assert.equal(create.params.headers.Authorization, 'Bearer jwt-101');
  assert.equal(create.params.headers['X-Dev-User'], undefined);
  assert.equal(harness.calls.at(-1).url.endsWith('/requests/23'), true);
  assert.equal(harness.checks.every(Boolean), true);
});

test('중간 계정 로그인 실패는 측정 전에 중단', () => {
  let index = 0;
  const harness = load({ post(url, body) {
    index++;
    if (index === 2) return response(401, null);
    return response(200, { accessToken: 'jwt', accessTokenExpiresIn: 1800,
      user: { id: index, email: JSON.parse(body).email, status: 'ACTIVE' } });
  } });
  assert.throws(() => harness.setup(), /load012.*실패/);
  assert.equal(index, 2);
});

test('거점이 없거나 중복이면 측정 전에 중단', () => {
  for (const hubs of [[], [{ id: 1, lat: 35, lng: 126 }, { id: 1, lat: 35, lng: 126 }]]) {
    const harness = load({ get(url) { return response(200, url.endsWith('/hubs') ? hubs : null); } });
    assert.throws(() => harness.setup(), /유효한 국내 거점/);
  }
});

test('기존 진행 요청 또는 응답 형식 오류는 자동 삭제 없이 중단', () => {
  for (const invalid of [response(200, { requestId: 9 }), response(200, null, true)]) {
    const harness = load({ get() { return invalid; } });
    assert.throws(() => harness.setup(), /사전 조회 실패/);
    assert.equal(harness.calls.some((call) => call.method === 'DELETE'), false);
  }
});

test('create 409 시 기존 요청을 조회·삭제하지 않는다', () => {
  const harness = load({ post() { return response(409, null); } });
  harness.iteration({ accounts: [{ token: 'jwt', userId: 101 }], hubs: [{ id: 98, lat: 35.17, lng: 126.9 }] });
  assert.equal(harness.calls.length, 0);
  assert.equal(harness.checks[0], false);
});

test('조회가 다른 ID 또는 잘못된 JSON을 반환해도 생성한 ID만 정리', () => {
  for (const invalid of [response(200, { requestId: 999, userId: 101 }), response(200, null, true)]) {
    const harness = load({ get() { return invalid; } });
    harness.iteration({ accounts: [{ token: 'jwt', userId: 101 }], hubs: [{ id: 98, lat: 35.17, lng: 126.9 }] });
    assert.equal(harness.calls.at(-1).url.endsWith('/requests/23'), true);
    assert.equal(harness.checks.includes(false), true);
  }
});

test('조회 중 예외에도 finally에서 현재 회차 요청을 정리', () => {
  const harness = load({ get() { throw new Error('transport failed'); } });
  assert.throws(() => harness.iteration({ accounts: [{ token: 'jwt', userId: 101 }],
    hubs: [{ id: 98, lat: 35.17, lng: 126.9 }] }), /transport failed/);
  assert.equal(harness.calls.at(-1).url.endsWith('/requests/23'), true);
});

test('원격 주소·누락 비밀번호·잘못된 VU 설정 거부', () => {
  assert.throws(() => load({}, { BASE_URL: 'https://example.com' }), /로컬 HTTP/);
  assert.throws(() => load({}, { LOAD_TEST_PASSWORD: '' }), /LOAD_TEST_PASSWORD/);
  assert.throws(() => load({}, { VUS: '1.5' }), /VUS/);
});

test('JSON 요약은 조건별 통계와 경로 모드를 포함하며 인증정보를 제외', () => {
  const harness = load();
  const result = harness.handleSummary({ metrics: {}, state: { testRunDurationMs: 1000 } });
  const output = result['load/k6/last-result.json'];
  assert.equal(JSON.parse(output).routeMode, 'fallback');
  assert.equal(output.includes('fixture-only'), false);
  assert.equal(output.includes('accessToken'), false);
});
