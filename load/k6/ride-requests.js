// JWT 인증을 포함한 요청 생성·조회·취소 부하 테스트. 저장소 루트에서 실행한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:18081').replace(/\/$/, '');
const VUS = positiveInteger(__ENV.VUS || '200', 'VUS', 1000);
const FIRST_USER_NUMBER = positiveInteger(__ENV.FIRST_USER_NUMBER || '11', 'FIRST_USER_NUMBER', 999000);
const RAMP_UP = __ENV.RAMP_UP || '30s';
const HOLD = __ENV.HOLD || '60s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';
const RUN_SECONDS = [RAMP_UP, HOLD, RAMP_DOWN].map(durationSeconds).reduce((a, b) => a + b, 0);
const RESULT_FILE = __ENV.RESULT_FILE || 'load/k6/last-result.json';
const ROUTE_MODE = __ENV.ROUTE_MODE || 'fallback';
const PASSWORD = __ENV.LOAD_TEST_PASSWORD;

// 측정 서버는 개발·실서비스 DB/Redis와 격리한 로컬 서버만 허용한다.
if (!/^http:\/\/(127\.0\.0\.1|localhost|\[::1\]):\d+$/.test(BASE_URL)) {
  throw new Error('BASE_URL은 포트를 지정한 로컬 HTTP 주소여야 합니다.');
}
if (!PASSWORD || !PASSWORD.trim()) throw new Error('LOAD_TEST_PASSWORD 환경변수가 필요합니다.');
if (RUN_SECONDS > 1200) throw new Error('토큰 만료를 피하려면 전체 측정 시간을 20분 이하로 설정하세요.');
if (!['fallback', 'mock'].includes(ROUTE_MODE)) throw new Error('ROUTE_MODE는 fallback 또는 mock이어야 합니다.');

const createTrend = new Trend('gachiga_create_ms', true);
const meTrend = new Trend('gachiga_me_ms', true);
const cancelTrend = new Trend('gachiga_cancel_ms', true);
const create409 = new Counter('gachiga_create_409');
const createOk = new Counter('gachiga_create_ok');
const cancelOk = new Counter('gachiga_cancel_ok');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    waiting_room: {
      executor: 'ramping-vus', startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '15s', gracefulStop: '15s',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:create}': ['p(95)<500'],
    'http_req_duration{endpoint:me}': ['p(95)<500'],
    'http_req_duration{endpoint:cancel}': ['p(95)<500'],
    'http_req_failed{phase:load}': ['rate<0.01'],
    gachiga_create_409: ['count==0'],
    gachiga_create_ok: ['count>0'],
    gachiga_cancel_ok: ['count>0'],
    checks: ['rate==1'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function positiveInteger(raw, name, max) {
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1 || value > max) throw new Error(`${name} 범위를 확인하세요 (1~${max}).`);
  return value;
}

function durationSeconds(value) {
  const matched = /^(\d+)(s|m)$/.exec(value);
  if (!matched || Number(matched[1]) < 1) throw new Error('시간은 양의 정수와 s/m으로 지정하세요. 예: 30s');
  return Number(matched[1]) * (matched[2] === 'm' ? 60 : 1);
}

function params(token, endpoint, phase = 'load') {
  return {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    tags: { endpoint, phase, name: endpoint === 'cancel' ? 'DELETE /api/requests/{id}' : endpoint },
    timeout: '10s', redirects: 0,
  };
}

function responseData(response) {
  try {
    const body = response.json();
    return body && body.success === true && Object.prototype.hasOwnProperty.call(body, 'data')
      ? { valid: true, data: body.data } : { valid: false };
  } catch (_) {
    return { valid: false };
  }
}

function validId(value) { return Number.isSafeInteger(value) && value > 0; }

/** 모든 VU 계정을 확인한다. 첫·마지막 계정만 확인하면 중간 시드 누락을 놓친다. */
export function setup() {
  const accounts = [];
  const userIds = new Set();
  const startedAt = Date.now();
  for (let index = 0; index < VUS; index++) {
    const email = `load${String(FIRST_USER_NUMBER + index).padStart(3, '0')}@jnu.ac.kr`;
    const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login', phase: 'setup' }, timeout: '10s', redirects: 0,
    });
    const parsed = responseData(login);
    const data = parsed.data;
    if (login.status !== 200 || !parsed.valid || !data || typeof data.accessToken !== 'string'
        || !data.accessToken.trim() || !data.user || !validId(data.user.id)
        || data.user.email !== email || data.user.status !== 'ACTIVE' || userIds.has(data.user.id)
        || !Number.isFinite(data.accessTokenExpiresIn) || data.accessTokenExpiresIn < RUN_SECONDS + 360) {
      throw new Error(`${email} 로그인/토큰 검증 실패 (HTTP ${login.status}). 시드와 비밀번호를 확인하세요.`);
    }
    const mine = http.get(`${BASE_URL}/api/requests/me`, params(data.accessToken, 'preflight', 'setup'));
    const current = responseData(mine);
    if (mine.status !== 200 || !current.valid || current.data !== null) {
      throw new Error(`${email} 사전 조회 실패 또는 진행 중인 요청이 있습니다. 격리 DB를 확인하세요.`);
    }
    userIds.add(data.user.id);
    accounts.push({ token: data.accessToken, userId: data.user.id });
  }
  const response = http.get(`${BASE_URL}/api/hubs`, params(accounts[0].token, 'hubs', 'setup'));
  const parsed = responseData(response);
  const hubs = parsed.data;
  if (response.status !== 200 || !parsed.valid || !Array.isArray(hubs) || hubs.length === 0
      || hubs.some((hub) => !hub || !validId(hub.id) || !Number.isFinite(hub.lat)
        || !Number.isFinite(hub.lng) || hub.lat < 33 || hub.lat > 39 || hub.lng < 124 || hub.lng > 132)
      || new Set(hubs.map((hub) => hub.id)).size !== hubs.length) {
    throw new Error('/api/hubs에 유효한 국내 거점 목록이 없습니다.');
  }
  if (Date.now() - startedAt > 300000) throw new Error('setup 시간이 5분을 넘었습니다. 토큰 수명을 확인하세요.');
  return { accounts, hubs };
}

function destinationNear(hub) {
  const meters = 3000 + Math.random() * 3000;
  const bearing = Math.random() * 2 * Math.PI;
  return {
    lat: hub.lat + meters * Math.cos(bearing) / 111320,
    lng: hub.lng + meters * Math.sin(bearing) / (111320 * Math.cos(hub.lat * Math.PI / 180)),
  };
}

export default function (data) {
  const account = data.accounts[__VU - 1];
  if (!account) throw new Error('VU에 해당하는 인증 계정이 없습니다.');
  const hub = data.hubs[Math.floor(Math.random() * data.hubs.length)];
  const dest = destinationNear(hub);
  const created = http.post(`${BASE_URL}/api/requests`, JSON.stringify({
    hubId: hub.id, destName: `부하 목적지 ${__VU}`, destLat: dest.lat, destLng: dest.lng,
    // 백엔드 Clock과 같은 KST. 실행 머신의 시간대에 영향받지 않는다.
    departAt: new Date(Date.now() + (9 * 60 + 5) * 60000).toISOString().slice(0, 19),
    maxWaitMin: [5, 10, 15, 20][Math.floor(Math.random() * 4)], sameGenderOnly: false,
    maxDetourRatio: [0.1, 0.2, 0.3][Math.floor(Math.random() * 3)],
  }), params(account.token, 'create'));
  const parsed = responseData(created);
  const request = parsed.data;
  create409.add(created.status === 409 ? 1 : 0);
  const valid = check(created, {
    'create 201 + own request': (r) => r.status === 201 && parsed.valid && request
      && validId(request.requestId) && request.userId === account.userId,
  });
  if (!valid) { sleep(0.5); return; }
  createOk.add(1);
  createTrend.add(created.timings.duration);

  // 현재 회차에서 만든 ID만 취소한다. 조회가 다른 ID를 반환해도 그 요청은 지우지 않는다.
  const requestId = request.requestId;
  try {
    for (let index = 0; index < 3; index++) {
      const mine = http.get(`${BASE_URL}/api/requests/me`, params(account.token, 'me'));
      const current = responseData(mine);
      const ok = check(mine, {
        'me 200 + same request': (r) => r.status === 200 && current.valid && current.data
          && current.data.requestId === requestId && current.data.userId === account.userId,
      });
      if (ok) meTrend.add(mine.timings.duration);
      sleep(0.3);
    }
  } finally {
    const cancelled = http.del(`${BASE_URL}/api/requests/${requestId}`, null, params(account.token, 'cancel'));
    const current = responseData(cancelled);
    const ok = check(cancelled, { 'cancel 200': (r) => r.status === 200 && current.valid && current.data === null });
    if (ok) { cancelTrend.add(cancelled.timings.duration); cancelOk.add(1); }
  }
  sleep(0.5);
}

/** 외부 JS CDN 없이 요약한다. 비밀번호·JWT는 결과 파일에 넣지 않는다. */
export function handleSummary(data) {
  const pick = (name) => data.metrics[name] ? data.metrics[name].values : {};
  const thresholds = {};
  Object.keys(data.metrics).forEach((name) => {
    if (data.metrics[name].thresholds) thresholds[name] = data.metrics[name].thresholds;
  });
  const out = {
    ranAt: new Date().toISOString(), baseUrl: BASE_URL, routeMode: ROUTE_MODE,
    vus: VUS, firstUserNumber: FIRST_USER_NUMBER, rampUp: RAMP_UP, hold: HOLD, rampDown: RAMP_DOWN,
    testRunDurationMs: data.state ? data.state.testRunDurationMs : null,
    iterations: pick('iterations').count || 0, http_reqs: pick('http_reqs').count || 0,
    http_reqs_per_s: pick('http_reqs').rate || 0,
    create_ok: pick('gachiga_create_ok').count || 0, cancel_ok: pick('gachiga_cancel_ok').count || 0,
    create_409: pick('gachiga_create_409').count || 0,
    failed_rate: pick('http_req_failed{phase:load}').rate, checks_rate: pick('checks').rate,
    create_ms: pick('gachiga_create_ms'), me_ms: pick('gachiga_me_ms'), cancel_ms: pick('gachiga_cancel_ms'),
    thresholds,
  };
  return { stdout: `${JSON.stringify(out, null, 2)}\n`, [RESULT_FILE]: JSON.stringify(out, null, 2) };
}
