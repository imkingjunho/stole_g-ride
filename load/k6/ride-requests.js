// 가치가 부하 테스트 — 매칭 요청 생성·조회·취소 (T3-1)
//
// 시나리오: 동시 200명. 각 VU 는 서로 다른 사용자(id 11~210, load/README.md 의 시드)로
//   POST /api/requests → GET /api/requests/me ×3 (대기 화면 폴링) → DELETE /api/requests/{id}
// 를 반복한다. 목표는 P95 < 500ms (PRD §10).
//
// 실행: k6 run load/k6/ride-requests.js            (기본 BASE_URL=http://127.0.0.1:8081)
//       k6 run -e BASE_URL=http://127.0.0.1:8080 -e VUS=50 -e HOLD=30s load/k6/ride-requests.js
// 주의: local 프로파일 기본 로그(SQL TRACE)는 지연을 크게 부풀린다. load/README.md 의 8081 기동 명령으로 측정한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8081';
const VUS = Number(__ENV.VUS || 200);
const HOLD = __ENV.HOLD || '60s';
const FIRST_USER_ID = Number(__ENV.FIRST_USER_ID || 11); // 시드 사용자 시작 id

// 거점 좌표 (route/StaticHubAdapter 와 같다). 목적지는 여기서 3~6km 떨어진 곳으로 만든다
const HUBS = [
  { id: 1, lat: 35.1724, lng: 126.9048 },
  { id: 2, lat: 35.1763, lng: 126.9123 },
  { id: 3, lat: 35.1784, lng: 126.9042 },
  { id: 4, lat: 35.1752, lng: 126.8923 },
  { id: 5, lat: 35.1604, lng: 126.8794 },
  { id: 6, lat: 35.1378, lng: 126.7902 },
];
const WAITS = [5, 10, 15, 20];
const DETOURS = [0.1, 0.2, 0.3];

const createTrend = new Trend('gachiga_create_ms', true);
const meTrend = new Trend('gachiga_me_ms', true);
const cancelTrend = new Trend('gachiga_cancel_ms', true);

export const options = {
  scenarios: {
    waiting_room: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS }, // 200명까지 올린다
        { duration: HOLD, target: VUS },  // 유지
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    // PRD §10: P95 < 500ms
    'http_req_duration{endpoint:create}': ['p(95)<500'],
    'http_req_duration{endpoint:me}': ['p(95)<500'],
    'http_req_duration{endpoint:cancel}': ['p(95)<500'],
    // 생성은 201 또는 409(전 회차 요청이 아직 살아 있음)만, 그 외는 실패
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function headers(userId) {
  return { 'Content-Type': 'application/json', 'X-Dev-User': String(userId) };
}

/** 거점에서 3~6km, 임의 방향. 500m 최소 거리(E-08)를 넉넉히 넘긴다 */
function destinationNear(hub) {
  const meters = 3000 + Math.random() * 3000;
  const bearing = Math.random() * 2 * Math.PI;
  const lat = hub.lat + (meters * Math.cos(bearing)) / 111320;
  const lng = hub.lng + (meters * Math.sin(bearing)) / (111320 * Math.cos((hub.lat * Math.PI) / 180));
  return { lat, lng };
}

/** 오프셋 없는 ISO 로컬 시각 — api-spec 이 Z·+09:00 을 거절한다 */
function localIsoPlusMinutes(min) {
  const d = new Date(Date.now() + min * 60000);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

export default function () {
  const userId = FIRST_USER_ID + (__VU - 1); // VU 마다 다른 사용자 (1인 1건 제한)
  const hub = HUBS[Math.floor(Math.random() * HUBS.length)];
  const dest = destinationNear(hub);

  // 1) 요청 생성
  const body = JSON.stringify({
    hubId: hub.id,
    destName: `부하 목적지 ${__VU}`,
    destLat: dest.lat,
    destLng: dest.lng,
    departAt: localIsoPlusMinutes(5),
    maxWaitMin: WAITS[Math.floor(Math.random() * WAITS.length)],
    sameGenderOnly: false,
    maxDetourRatio: DETOURS[Math.floor(Math.random() * DETOURS.length)],
  });
  const created = http.post(`${BASE_URL}/api/requests`, body, {
    headers: headers(userId),
    tags: { endpoint: 'create' },
  });
  createTrend.add(created.timings.duration);
  check(created, { 'create 201/409': (r) => r.status === 201 || r.status === 409 });

  // 2) 대기 화면 폴링처럼 조회 3번
  let requestId = null;
  for (let i = 0; i < 3; i++) {
    const me = http.get(`${BASE_URL}/api/requests/me`, {
      headers: headers(userId),
      tags: { endpoint: 'me' },
    });
    meTrend.add(me.timings.duration);
    const ok = check(me, { 'me 200': (r) => r.status === 200 });
    if (ok) {
      const data = me.json('data');
      if (data && data.requestId) requestId = data.requestId;
    }
    sleep(0.3);
  }

  // 3) 취소 — 다음 회차에 같은 사용자가 또 만들 수 있게
  if (requestId) {
    const cancelled = http.del(`${BASE_URL}/api/requests/${requestId}`, null, {
      headers: headers(userId),
      // name 을 고정해야 요청 id 마다 시계열이 갈라지지 않는다 (k6 고유 시계열 경고)
      tags: { endpoint: 'cancel', name: 'DELETE /api/requests/{id}' },
    });
    cancelTrend.add(cancelled.timings.duration);
    check(cancelled, { 'cancel 200': (r) => r.status === 200 });
  }
  sleep(0.5);
}

/** 결과를 콘솔과 JSON 으로 남긴다. docs/performance.md 에 옮겨 적는다 */
export function handleSummary(data) {
  const pick = (name) => {
    const m = data.metrics[name];
    return m ? m.values : {};
  };
  const out = {
    ranAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    vus: VUS,
    hold: HOLD,
    iterations: pick('iterations').count,
    http_reqs: pick('http_reqs').count,
    failed_rate: pick('http_req_failed').rate,
    checks_rate: pick('checks').rate,
    create_ms: pick('gachiga_create_ms'),
    me_ms: pick('gachiga_me_ms'),
    cancel_ms: pick('gachiga_cancel_ms'),
  };
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'load/k6/last-result.json': JSON.stringify(out, null, 2),
  };
}
