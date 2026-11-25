import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { chromium } = require(
  process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES
    ? `${process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES}/playwright`
    : 'playwright',
);
const root = fileURLToPath(new URL('..', import.meta.url));
const output = process.env.QA_OUTPUT ?? `${root}/qa-output`;
await mkdir(output, { recursive: true });
const server = spawn(
  process.execPath,
  ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5186', '--strictPort'],
  { cwd: root, env: { ...process.env, VITE_API_MODE: 'mock', VITE_KAKAO_JS_KEY: 'qa-sdk-stub' } },
);
await new Promise((resolve, reject) => {
  server.stdout.on('data', (b) => {
    if (b.toString().includes('Local:')) resolve();
  });
  server.stderr.on('data', (b) => process.stderr.write(b));
  server.on('error', reject);
  server.on('exit', (code) => {
    if (code) reject(new Error(`Vite ${code}`));
  });
});
const browser = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  headless: true,
  args: [
    '--no-sandbox',
    '--disable-dev-shm-usage',
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--no-zygote',
  ],
});
const context = await browser.newContext({
  viewport: { width: 360, height: 800 },
  locale: 'ko-KR',
  reducedMotion: 'reduce',
});
// 실제 카카오 서버를 호출하지 않고 SDK 계약(Polyline·정차 마커·bounds)을 검사한다.
await context.route('https://dapi.kakao.com/**', (route) =>
  route.fulfill({
    contentType: 'application/javascript',
    body: `
    (() => { window.__mapOverlays = [];
    class LatLng { constructor(lat,lng){this.lat=lat;this.lng=lng} getLat(){return this.lat} getLng(){return this.lng} }
    class Map { setLevel(level){this.level=level} setCenter(center){this.center=center} setBounds(b){this.bounds=b;window.__lastBounds=b.points.map(p=>({lat:p.getLat(),lng:p.getLng()}))} setZoomable(){} }
    class Bounds { constructor(){this.points=[]} extend(p){this.points.push(p)} }
    class Overlay { constructor(options){Object.assign(this,options);window.__mapOverlays.push(this)} setMap(map){this.map=map} }
    window.kakao={maps:{LatLng,Map,LatLngBounds:Bounds,Polyline:Overlay,CustomOverlay:Overlay,Marker:Overlay,event:{addListener(map,name,fn){if(name==='click')window.__tapMap=fn},removeListener(map,name,fn){if(window.__tapMap===fn)delete window.__tapMap}},load(cb){cb()}}}; })();
  `,
  }),
);
const page = await context.newPage();
page.setDefaultTimeout(15000);
page.setDefaultNavigationTimeout(15000);
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
const checks = [];
const base = 'http://127.0.0.1:5186';
async function check(name, body) {
  await body();
  checks.push(name);
  console.log(`PASS ${name}`);
}
async function navigate(path) {
  await page.evaluate(async (p) => {
    const { router } = await import('/src/app/router.tsx');
    await router.navigate(p);
  }, path);
}
async function waitMapStyle(style) {
  await page.waitForFunction((expected) => {
    const lines = window.__mapOverlays.filter((o) => o.map && o.path);
    return lines.length === 3 && lines.every((o) => o.strokeStyle === expected);
  }, style);
}
async function demo(pending = false) {
  while (await page.getByRole('button', { name: '알림 닫기', exact: true }).count())
    await page.getByRole('button', { name: '알림 닫기', exact: true }).first().click();
  await navigate('/dev/demo');
  await page
    .getByRole('button', { name: pending ? '60초 수락 화면 보기' : '성사 화면 보기' })
    .click();
  await page
    .getByRole('heading', { name: pending ? '동승 제안이 도착했어요' : '동승이 성사됐어요!' })
    .waitFor();
}
try {
  await check('비로그인 접근 차단', async () => {
    await page.goto(`${base}/history`);
    await page.waitForURL('**/login');
  });
  await check('관리자 로그인 후 운영 화면·일반 계정 접근 제한', async () => {
    await page.goto(base + '/admin');
    await page.getByRole('heading', { name: '관리자 로그인' }).waitFor();
    await page.getByLabel('이메일').fill('admin@gachiga.invalid');
    await page.getByLabel('비밀번호').fill('mock-preview');
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await page.waitForURL('**/admin');
    await page.getByRole('heading', { name: '매칭 운영 현황' }).waitFor();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.setViewportSize({ width: 1440, height: 1440 });
    const width = await page.locator('.ops-body').evaluate((el) => el.clientWidth);
    assert.ok(width > 1100);
    assert.equal(await page.getByRole('navigation', { name: '주요 메뉴' }).count(), 0);
    const ended = page.getByRole('table', { name: '거점별 만료 및 취소 요청' });
    assert.match(
      await ended.getByRole('row', { name: /전남대 후문 스타벅스/ }).innerText(),
      /4건\s+1건\s+5건/,
    );
    assert.equal(await page.getByRole('heading', { name: '매칭 운영 현황' }).count(), 1);
    await page.screenshot({ path: output + '/admin-desktop.png' });
    await page.getByRole('button', { name: /남은 시간 2분 이내/ }).click();
    const roster = page.getByRole('table', { name: '현재 참여자 목록' });
    assert.equal(await roster.locator('tbody tr').count(), 2);
    await page.getByRole('button', { name: '전남대 정문 이디야 커피', exact: true }).click();
    assert.equal(await roster.locator('tbody tr').count(), 1);
    assert.match(await roster.innerText(), /정문다람쥐/);
    await page.getByLabel('거점 필터').selectOption('');
    await page.getByRole('button', { name: '매칭된 사람', exact: true }).click();
    assert.equal(await roster.locator('tbody tr').count(), 3);
    await page.getByLabel('닉네임 검색').fill('일치하는이름없음');
    await page.getByText('조건에 맞는 참여자가 없어요.').waitFor();
    const boundary = await page.evaluate(async () => {
      const { readOperations } = await import('/src/features/admin/operations.ts');
      const waiting = ['2026-11-25T15:02:00', '2026-11-25T15:02:01', undefined].map(
        (expiresAt, i) => ({
          nickname: String(i),
          hubName: '전남대 정문',
          requestedAt: '2026-11-25T14:58:00',
          expiresAt,
        }),
      );
      const ops = readOperations({ waiting, matched: [], daily: [], asOf: '2026-11-25T15:00:00' });
      return [ops.expiring, ops.expiresKnown, ops.hubs[0].longest];
    });
    assert.deepEqual(boundary, [1, false, 2]);
    await page.setViewportSize({ width: 360, height: 800 });
    await navigate('/me');
    await page.getByRole('button', { name: '로그아웃', exact: true }).click();
    await page.waitForURL('**/login');
    await page.getByRole('button', { name: '목 데이터로 둘러보기' }).click();
    await page.waitForURL('**/request');
    await navigate('/admin');
    await page.getByRole('heading', { name: '관리자 권한이 필요해요' }).waitFor();
    await navigate('/me');
    await page.getByRole('button', { name: '로그아웃', exact: true }).click();
    await page.waitForURL('**/login');
  });
  await check('회원가입: 코드 오류 복귀·재입력·프로필 등록', async () => {
    await page.getByRole('link', { name: '학교 이메일로 회원가입' }).click();
    await page.getByLabel('학교 이메일').fill('student@jnu.ac.kr');
    await page.getByRole('button', { name: '인증 코드 받기' }).click();
    await page.getByLabel('인증 코드').fill('000000');
    await page.getByRole('button', { name: '프로필 입력으로' }).click();
    await page.getByLabel('닉네임').fill('테스트동승자');
    await page.getByLabel('비밀번호').fill('safe-password');
    await page.getByRole('button', { name: '가입 완료' }).click();
    await page.waitForURL('**/signup/verify');
    await page.getByLabel('인증 코드').fill('482913');
    await page.getByRole('button', { name: '프로필 입력으로' }).click();
    await page.getByLabel('닉네임').fill('테스트동승자');
    await page.getByLabel('비밀번호').fill('safe-password');
    await page.getByRole('button', { name: '가입 완료' }).click();
    await page.waitForURL('**/login');
  });
  await check('로그인·성사·정산 합계·모바일', async () => {
    await page.getByRole('button', { name: '목 데이터로 둘러보기' }).click();
    await page.waitForURL(/\/(request|waiting)$/);
    await demo();
    await page.getByText('함께 7,895원', { exact: true }).waitFor();
    await page.getByRole('link', { name: '내 요금은 어떻게 계산됐나요? →' }).click();
    await page.getByRole('heading', { name: '거리 비율로 계산해요' }).waitFor();
    const sums = await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      const g = mockState.group;
      return {
        total: g.totalFare,
        sum: g.members.reduce((s, m) => s + m.shareAmount, 0),
        validShares:
          g.fareMethod === 'DISTANCE_RATIO' &&
          g.members.every(
            (m) =>
              m.soloDistance > 0 &&
              m.shareAmount >= 0 &&
              m.shareAmount <= m.soloFare &&
              m.savingAmount === m.soloFare - m.shareAmount,
          ),
      };
    });
    assert.equal(sums.total, sums.sum);
    assert.ok(sums.validShares);
    for (const width of [360, 440, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      for (const route of ['/groups/17', '/groups/17/fare', '/groups/17/chat', '/history', '/me']) {
        await navigate(route);
        await page.locator('main h1').waitFor();
        assert.ok(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
          `${width} ${route}`,
        );
      }
    }
    await page.setViewportSize({ width: 360, height: 800 });
  });
  await check('하단 탭 이동·스크롤 숨김과 복귀·요청 단계 모션', async () => {
    await page.setViewportSize({ width: 360, height: 420 });
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await navigate('/dev/demo');
    await page.getByRole('button', { name: '새 요청 작성', exact: true }).click();
    const origin = page.getByRole('button', { name: '전남대 후문 스타벅스', exact: true });
    await origin.waitFor();
    const nav = page.getByRole('navigation', { name: '주요 메뉴' });
    const pill = nav.locator('.floating-tab-pill');
    const pillX = () => pill.evaluate((e) => e.getBoundingClientRect().x);
    const requestX = await nav
      .getByRole('link', { name: '요청', exact: true })
      .evaluate((e) => e.getBoundingClientRect().x);
    await page.evaluate(() => window.scrollTo(0, 110));
    await page.waitForFunction(
      () => document.querySelector('.floating-tabs').getBoundingClientRect().top >= innerHeight,
    );
    await page.evaluate(() => window.scrollBy(0, -35));
    await page.waitForFunction(
      () => document.querySelector('.floating-tabs').getBoundingClientRect().bottom <= innerHeight,
    );
    const waiting = nav.getByRole('link', { name: '대기', exact: true });
    await waiting.click();
    await page.waitForURL('**/waiting');
    await page.waitForFunction(() => {
      const pill = document.querySelector('.floating-tab-pill').getBoundingClientRect();
      const tab = document.querySelector('.floating-tab[href="/waiting"][aria-current="page"]');
      return tab && Math.abs(pill.x - tab.getBoundingClientRect().x) < 1;
    });
    assert.ok((await pillX()) > requestX + 40);
    await nav.getByRole('link', { name: '요청', exact: true }).click();
    const progress = page.getByRole('progressbar', { name: '동승 요청 단계' });
    assert.equal(await progress.getAttribute('aria-valuenow'), '1');
    const second = progress.locator('span').nth(1);
    assert.ok(
      await second.evaluate(
        (e) => parseFloat(getComputedStyle(e, '::before').transitionDuration) > 0.1,
      ),
    );
    await origin.click();
    assert.equal(await progress.getAttribute('aria-valuenow'), '2');
    await page.waitForFunction(
      () =>
        new DOMMatrixReadOnly(
          getComputedStyle(document.querySelector('.trip-track > span:nth-child(2)'), '::before')
            .transform,
        ).a > 0.99,
    );
    await page.getByRole('button', { name: '이전 단계' }).click();
    await page.waitForFunction(
      () =>
        new DOMMatrixReadOnly(
          getComputedStyle(document.querySelector('.trip-track > span:nth-child(2)'), '::before')
            .transform,
        ).a < 0.01,
    );
    await page.emulateMedia({ reducedMotion: 'reduce' });
    assert.ok(
      await second.evaluate(
        (e) => parseFloat(getComputedStyle(e, '::before').transitionDuration) < 0.001,
      ),
    );
    await page.setViewportSize({ width: 360, height: 800 });
  });
  await check('여섯 승차 지점의 지도 핀이 선택한 장소와 일치', async () => {
    await navigate('/request');
    for (const [name, lat, lng] of [
      ['전남대 정문 이디야 커피', 35.172306, 126.905304],
      ['전남대 후문 스타벅스', 35.177217, 126.912457],
      ['전대 예대점 CU', 35.180726, 126.904215],
      ['경신여고 새약손 한방병원', 35.17305, 126.887698],
      ['유스퀘어 택시정류장', 35.161046, 126.87851],
      ['광주송정역 택시정류장', 35.136335, 126.7903],
    ]) {
      await page.getByRole('button', { name, exact: true }).click();
      await page.waitForFunction(
        ([y, x]) =>
          window.__mapOverlays.some(
            (o) =>
              o.map?.level === 3 &&
              Math.abs(o.map.center.getLat() - y) < 0.000001 &&
              Math.abs(o.map.center.getLng() - x) < 0.000001 &&
              o.content?.textContent === '출발' &&
              Math.abs(o.position.getLat() - y) < 0.000001 &&
              Math.abs(o.position.getLng() - x) < 0.000001,
          ),
        [lat, lng],
      );
      await page.getByRole('button', { name: '이전 단계' }).click();
    }
  });
  await check('요청 생성·중복 방지·취소·빈 대기 화면', async () => {
    await navigate('/dev/demo');
    await page.getByRole('button', { name: '새 요청 작성', exact: true }).click();
    await page.getByRole('heading', { name: '어디에서 출발할까요?' }).waitFor();
    await page.getByRole('button', { name: '전남대 후문 스타벅스', exact: true }).click();
    await page.getByLabel('목적지 검색').fill('송정');
    await page.getByRole('region', { name: '장소 검색 결과' }).getByRole('button').first().click();
    await page.waitForFunction(() => {
      const labels = window.__mapOverlays
        .filter((overlay) => overlay.map)
        .map((overlay) => overlay.content?.textContent);
      return labels.includes('출발') && labels.includes('도착');
    });
    await page.evaluate(() =>
      window.__tapMap({ latLng: new window.kakao.maps.LatLng(35.1379, 126.7903) }),
    );
    await page.getByRole('heading', { name: '광주송정역 인근' }).waitFor();
    await page.getByRole('button', { name: '여기로 도착' }).click();
    await page.getByRole('radio', { name: '3명', exact: true }).check();
    await page.getByRole('radio', { name: '5분', exact: true }).check();
    assert.ok(await page.getByRole('radio', { name: '성별 무관' }).isChecked());
    assert.ok(await page.getByRole('radio', { name: '20%', exact: true }).isChecked());
    await page.getByRole('button', { name: '요청 확인', exact: true }).click();
    await page.waitForFunction(() =>
      window.__mapOverlays.some(
        (o) =>
          o.map &&
          o.strokeStyle === 'dash' &&
          o.path?.length === 2 &&
          Math.abs(o.path[0].getLat() - 35.177217) < 0.000001 &&
          Math.abs(o.path[0].getLng() - 126.912457) < 0.000001 &&
          o.path[1].getLat() === 35.1379 &&
          o.path[1].getLng() === 126.7903,
      ),
    );
    await page.getByRole('button', { name: /대기 시간.*5분/ }).click();
    assert.ok(await page.getByRole('radio', { name: '5분', exact: true }).isChecked());
    await page.getByRole('button', { name: '요청 확인', exact: true }).click();
    await page.getByRole('button', { name: '동승 요청하기' }).click();
    await page.waitForURL('**/waiting');
    await page.getByRole('heading', { name: '함께 갈 사람을 찾고 있어요' }).waitFor();
    const request = await page.evaluate(
      async () => (await import('/src/shared/mocks/state.ts')).mockState.request,
    );
    assert.equal(request.preferredSize, 3);
    assert.equal(request.hub.id, 2);
    assert.equal(request.maxWaitMin, 5);
    assert.equal(request.destName, '광주송정역 인근');
    assert.equal(request.destLat, 35.1379);
    assert.equal(request.destLng, 126.7903);
    assert.ok(request.remainingSeconds > 290 && request.remainingSeconds <= 300);
    const duplicate = await page.evaluate(async () => {
      const { api } = await import('/src/shared/api/client.ts');
      const { mockState } = await import('/src/shared/mocks/state.ts');
      const r = mockState.request;
      const result = await api.POST('/api/requests', { body: { ...r, hubId: r.hub.id } });
      mockState.failures.set('DELETE /api/requests/' + r.requestId, 1);
      return { status: result.response.status, code: result.error?.error.code };
    });
    assert.equal(duplicate.status, 409);
    assert.equal(duplicate.code, 'ALREADY_IN_QUEUE');
    await page.getByRole('button', { name: '요청 취소', exact: true }).click();
    await page.getByText('일시적으로 정보를 불러오지 못했어요.', { exact: true }).waitFor();
    assert.ok(
      await page.evaluate(
        async () => !!(await import('/src/shared/mocks/state.ts')).mockState.request,
      ),
    );
    await page.getByRole('button', { name: '요청 취소', exact: true }).click();
    await page.waitForURL('**/request');
    await navigate('/waiting');
    await page.getByRole('heading', { name: '진행 중인 요청이 없어요' }).waitFor();
  });
  await check('요청 만료·재시도·목적지 검색 빈 상태', async () => {
    await navigate('/dev/demo');
    await page.getByRole('button', { name: '대기 만료 화면 보기' }).click();
    await page.getByRole('heading', { name: '대기 시간이 끝났어요' }).waitFor();
    await page.getByRole('button', { name: '다시 매칭 요청하기' }).click();
    await page.waitForURL('**/request');
    await page.getByRole('button', { name: '전남대 후문 스타벅스', exact: true }).click();
    await page.getByLabel('목적지 검색').fill('없는목적지');
    await page.getByText('검색 결과가 없어요.', { exact: true }).waitFor();
    await page.getByLabel('목적지 검색').fill('유스');
    await page.getByRole('region', { name: '장소 검색 결과' }).getByRole('button').first().click();
    await page.getByRole('button', { name: '여기로 도착' }).click();
    await page.getByRole('button', { name: '요청 확인', exact: true }).click();
    await page.getByRole('button', { name: '동승 요청하기' }).click();
    await page.waitForURL('**/waiting');
    await page.getByRole('heading', { name: '함께 갈 사람을 찾고 있어요' }).waitFor();
    await page.getByRole('button', { name: '요청 취소', exact: true }).click();
    await page.waitForURL('**/request');
  });
  await check('API 오류: 그룹 재시도·이력과 내 정보의 통계 재시도', async () => {
    await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      mockState.failures.set('GET /api/groups/17', 2);
    });
    await navigate('/dev/demo');
    await page.getByRole('button', { name: '성사 화면 보기', exact: true }).click();
    await page
      .locator('main')
      .getByRole('alert')
      .filter({ hasText: '일시적으로 정보를 불러오지 못했어요.' })
      .waitFor();
    await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
    for (const path of ['/history', '/me']) {
      await page.evaluate(async () =>
        (await import('/src/shared/mocks/state.ts')).mockState.failures.set('GET /api/stats/me', 1),
      );
      await navigate(path);
      await page
        .locator('main')
        .getByRole('alert')
        .filter({ hasText: '일시적으로 정보를 불러오지 못했어요.' })
        .waitFor();
      await page.getByRole('button', { name: '다시 시도', exact: true }).click();
      await page
        .locator('main')
        .getByRole('alert')
        .filter({ hasText: '일시적으로 정보를 불러오지 못했어요.' })
        .waitFor({ state: 'hidden' });
    }
  });
  await check('네트워크 실패 안내·채팅 연결 끊김과 복구', async () => {
    await demo();
    await page.getByRole('link', { name: '동승자와 채팅하기' }).click();
    await page.getByLabel('메시지', { exact: true }).fill('연결 확인');
    await page.evaluate(async () =>
      (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.setState({
        status: 'disconnected',
      }),
    );
    await page.getByText('연결이 끊겼어요. 다시 연결하고 있어요.', { exact: true }).waitFor();
    assert.ok(await page.getByRole('button', { name: '보내기', exact: true }).isDisabled());
    await page.evaluate(async () =>
      (await import('/src/shared/ws/connectionStore.ts')).useConnectionStore.setState({
        status: 'connected',
      }),
    );
    await page.getByRole('button', { name: '보내기', exact: true }).waitFor({ state: 'visible' });
    assert.equal(
      await page.getByRole('button', { name: '보내기', exact: true }).isDisabled(),
      false,
    );
    await page.evaluate(() => {
      window.__savedFetch = window.fetch;
      window.fetch = async (...args) => {
        const input = args[0];
        const url = typeof input === 'string' ? input : input.url;
        if (url?.includes('/api/groups/17') && !url.includes('/messages'))
          throw new TypeError('Failed to fetch');
        return window.__savedFetch(...args);
      };
    });
    try {
      await navigate('/groups/17');
      await page
        .locator('main')
        .getByRole('alert')
        .filter({ hasText: '네트워크 상태를 확인하고 다시 시도해 주세요.' })
        .waitFor();
      assert.equal(await page.getByRole('button', { name: '수락', exact: true }).count(), 0);
    } finally {
      await page.evaluate(() => {
        window.fetch = window.__savedFetch;
        delete window.__savedFetch;
      });
    }
    await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
  });
  await check('성사 지도: 서버 경로·마커 순서·좌표 누락·추정치', async () => {
    await demo();
    await page.getByRole('application', { name: '합승 경로 지도' }).waitFor();
    await page.waitForFunction(() => window.__mapOverlays.some((o) => o.map && o.content));
    await waitMapStyle('solid');
    const overlays = await page.evaluate(() =>
      window.__mapOverlays
        .filter((o) => o.map)
        .map((o) => ({
          marker: o.content?.textContent,
          lat: o.position?.getLat(),
          lng: o.position?.getLng(),
          points: o.path?.length,
        })),
    );
    const markers = overlays.filter((o) => o.marker);
    assert.equal(markers.length, 4);
    assert.match(markers[0].marker, /탑승.*전남대 후문/);
    assert.match(markers[3].marker, /하차.*광주송정역/);
    assert.equal(markers[3].lat, 35.1378);
    assert.equal(overlays.filter((o) => o.points > 1).length, 3);
    await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      delete mockState.group.members[0].destLat;
      mockState.group.estimated = true;
    });
    await navigate('/me');
    await navigate('/groups/17');
    await page
      .getByText(
        '일부 하차 위치가 아직 없어 지도에 표시되지 않아요. 아래 하차 순서를 확인해 주세요.',
      )
      .waitFor();
    await page.getByText('추정 경로예요. 실제 도로와 이동 시간은 다를 수 있어요.').waitFor();
    await waitMapStyle('dash');
    await page.screenshot({ path: `${output}/group-map.png`, fullPage: true });
    await demo();
  });
  await check('DemoPage 샘플 JSON 지도: 실선·점선 전환·마커·전체 bounds', async () => {
    await navigate('/dev/demo');
    await page.getByRole('heading', { name: '샘플 경로 지도 확인' }).waitFor();
    await page.getByRole('application', { name: '합승 경로 지도' }).waitFor();
    await page.waitForFunction(() => window.__lastBounds?.length === 10);
    const bounds = await page.evaluate(() => window.__lastBounds);
    assert.ok(bounds.some((point) => point.lat === 35.1763 && point.lng === 126.9123));
    assert.ok(bounds.some((point) => point.lat === 35.1378 && point.lng === 126.7902));
    await waitMapStyle('solid');
    await page.getByLabel('추정 경로 점선 확인').check();
    await waitMapStyle('dash');
    await page.getByText('추정 경로예요. 실제 도로와 이동 시간은 다를 수 있어요.').waitFor();
    await page.getByText('샘플 경로 JSON 보기', { exact: true }).click();
    const sample = JSON.parse(await page.locator('details pre').textContent());
    assert.equal(sample.route.sections.length, 3);
    assert.equal(sample.members.length, 3);
    assert.equal(sample.estimated, true);
    await page.screenshot({ path: `${output}/demo-map-dashed.png`, fullPage: true });
    await page.getByLabel('추정 경로 점선 확인').uncheck();
    await waitMapStyle('solid');
    assert.equal(await page.evaluate(() => window.__mapOverlays.filter((o) => o.map).length), 7);
    assert.equal(await page.evaluate(() => window.__lastBounds.length), 10);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  });
  await check('모달 포커스 제한·Escape·포커스 복귀', async () => {
    await navigate('/dev/ui');
    await page.getByRole('button', { name: '모달 열기' }).click();
    await page.getByRole('dialog').waitFor();
    for (let i = 0; i < 5; i++) {
      await page.keyboard.press('Tab');
      assert.ok(
        await page.evaluate(() =>
          document.querySelector('dialog').contains(document.activeElement),
        ),
      );
    }
    await page.keyboard.press('Escape');
    assert.equal(
      await page
        .getByRole('button', { name: '모달 열기' })
        .evaluate((el) => el === document.activeElement),
      true,
    );
    await page.getByRole('button', { name: '하단 시트 열기' }).click();
    await page.getByRole('dialog').waitFor();
    await page.keyboard.press('Escape');
  });
  await check('수락·거절·만료·재계산 안내', async () => {
    await demo(true);
    await page.getByRole('button', { name: '수락', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
    await page.evaluate(async () => {
      const { mockState, notifyMatch } = await import('/src/shared/mocks/state.ts');
      mockState.group.status = 'PENDING';
      mockState.group.acceptDeadline = new Date(Date.now() + 60000).toISOString();
      mockState.group.members.find((m) => m.isMe).shareAmount = 7800;
      mockState.group.members.forEach((m) => (m.accepted = false));
      notifyMatch('RECALCULATED', '경로가 변경됐어요.');
    });
    await page.getByText('7,895원 → 7,800원', { exact: true }).waitFor();
    await page.getByRole('button', { name: '거절', exact: true }).click();
    await page.waitForURL('**/waiting');
    await demo(true);
    await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      mockState.group.acceptDeadline = new Date(Date.now() - 1000).toISOString();
      const { stompClient } = await import('/src/shared/ws/stompClient.ts');
      void stompClient;
      const { notifyMatch } = await import('/src/shared/mocks/state.ts');
      notifyMatch('RECALCULATED', '응답 시간이 지났어요.');
    });
    await page.getByRole('heading', { name: '응답 시간이 지났어요' }).waitFor();
    assert.ok(await page.getByRole('button', { name: '수락', exact: true }).isDisabled());
  });
  await check('채팅 송수신·마스킹·재진입 중복 방지·신고', async () => {
    await demo();
    await page.getByRole('link', { name: '동승자와 채팅하기' }).click();
    await page.getByLabel('메시지', { exact: true }).fill('후문에서 만나요');
    await page.getByRole('button', { name: '보내기', exact: true }).click();
    await page.getByText('후문에서 만나요', { exact: true }).waitFor();
    await page.getByRole('button', { name: '도착했어요', exact: true }).click();
    await page.getByRole('log').getByText('도착했어요', { exact: true }).waitFor();
    assert.equal(await page.getByRole('log').getByText('도착했어요', { exact: true }).count(), 1);
    await page.getByLabel('메시지', { exact: true }).fill('test@example.com');
    await page.getByRole('button', { name: '보내기', exact: true }).click();
    await page.getByText('개인정보가 가려진 메시지', { exact: true }).waitFor();
    await navigate('/groups/17');
    await page.getByRole('link', { name: '동승자와 채팅하기' }).click();
    await page.getByRole('log').getByText('후문에서 만나요', { exact: true }).waitFor();
    assert.equal(
      await page.getByRole('log').getByText('후문에서 만나요', { exact: true }).count(),
      1,
    );
    await page.getByLabel('메시지', { exact: true }).fill('   ');
    assert.ok(await page.getByRole('button', { name: '보내기', exact: true }).isDisabled());
    await page.getByLabel('채팅 메뉴').click();
    await page.getByRole('button', { name: '동승자 신고' }).click();
    await page.getByLabel('신고할 동승자', { exact: true }).selectOption('2');
    await page.getByRole('button', { name: '신고 제출' }).click();
    await page.getByText('신고가 접수됐어요.', { exact: true }).waitFor();
  });
  await check('이력 필터·빈 상태·프로필·완료', async () => {
    await navigate('/history');
    await page.getByLabel('조회 월').fill('2025-01');
    await page.getByText('이 기간의 동승 이력이 없어요.', { exact: true }).waitFor();
    await page.getByRole('button', { name: '전체 기간 보기' }).click();
    await page.getByRole('link', { name: '정산 상세', exact: true }).waitFor();
    await navigate('/me');
    await page.getByRole('button', { name: '프로필 수정' }).click();
    await page.getByLabel('닉네임').fill('새닉네임');
    await page.getByRole('button', { name: '저장', exact: true }).click();
    await page.getByRole('heading', { name: '새닉네임', exact: true }).waitFor();
    await navigate('/groups/17');
    await page.getByRole('button', { name: '탑승 완료', exact: true }).click();
    await page.getByRole('button', { name: '탑승 완료 확인' }).click();
    await page.waitForURL('**/groups/17/fare');
    await page.getByRole('heading', { name: '동승자별 분담액' }).waitFor();
    await page.getByRole('link', { name: '함께한 이동 보기' }).click();
    await page.waitForURL('**/history');
  });
  await check('그룹 응답 계약 오류·내 정보 위조 방지·복구', async () => {
    await demo();
    await page.evaluate(async () => {
      window.__groupFetch = window.fetch;
      window.__groupMode = 'bare';
      window.fetch = async (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (new URL(url).pathname === '/api/groups/17') {
          const { mockState } = await import('/src/shared/mocks/state.ts');
          const group = structuredClone(mockState.group);
          if (window.__groupMode === 'bare') {
            // main GroupResponse의 현재 형식: ApiResponse·hub·route·isMe 등이 빠져 있다.
            return Response.json({
              id: group.groupId,
              hubId: group.hub.id,
              status: group.status,
              totalFare: group.totalFare,
              members: group.members.map((m) => ({ userId: m.userId, shareAmount: m.shareAmount })),
            });
          }
          if (window.__groupMode === 'status') group.status = ['CONFIRMED'];
          if (window.__groupMode === 'member') group.members.find((m) => m.isMe).userId = 999;
          return Response.json({ success: true, data: group, error: null });
        }
        return window.__groupFetch(input, init);
      };
    });
    try {
      await navigate('/me');
      await navigate('/groups/17');
      await page.getByRole('alert').filter({ hasText: '동승 정보를 확인하지 못했어요.' }).waitFor();
      assert.equal(await page.getByRole('button', { name: '탑승 완료', exact: true }).count(), 0);
      await page.evaluate(() => {
        window.__groupMode = 'status';
      });
      await page.getByRole('button', { name: '다시 시도', exact: true }).click();
      await page.getByRole('alert').filter({ hasText: '동승 정보를 확인하지 못했어요.' }).waitFor();
      await page.evaluate(() => {
        window.__groupMode = 'member';
      });
      await page.getByRole('button', { name: '다시 시도', exact: true }).click();
      await page.waitForTimeout(200);
      await page.getByRole('alert').filter({ hasText: '동승 정보를 확인하지 못했어요.' }).waitFor();
      assert.equal(await page.getByRole('link', { name: '동승자와 채팅하기' }).count(), 0);
    } finally {
      await page.evaluate(() => {
        window.fetch = window.__groupFetch;
        delete window.__groupFetch;
        delete window.__groupMode;
      });
    }
    await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
  });
  await check('비구성원·잘못된 그룹 주소에서 채팅 구독과 조회 차단', async () => {
    await page.evaluate(async () => {
      const { stompClient } = await import('/src/shared/ws/stompClient.ts');
      window.__groupSubscribe = stompClient.subscribe.bind(stompClient);
      window.__groupSubscriptions = [];
      stompClient.subscribe = (destination, handler) => {
        if (destination.startsWith('/topic/chat/')) window.__groupSubscriptions.push(destination);
        return window.__groupSubscribe(destination, handler);
      };
      window.__groupFetch = window.fetch;
      window.__messageReads = 0;
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        const path = new URL(url).pathname;
        if (path === '/api/groups/999/messages') window.__messageReads++;
        if (path === '/api/groups/999')
          return Promise.resolve(
            Response.json(
              {
                success: false,
                data: null,
                error: { code: 'GROUP_NOT_MEMBER', message: '그룹 구성원이 아닙니다.' },
              },
              { status: 403 },
            ),
          );
        return window.__groupFetch(input, init);
      };
    });
    try {
      await navigate('/groups/999/chat');
      await page
        .getByRole('alert')
        .filter({ hasText: '이 동승 그룹에 접근할 수 없어요.' })
        .waitFor();
      assert.deepEqual(await page.evaluate(() => window.__groupSubscriptions), []);
      assert.equal(await page.evaluate(() => window.__messageReads), 0);
      assert.equal(await page.getByLabel('메시지', { exact: true }).count(), 0);
      await navigate('/groups/not-a-number/chat');
      await page.getByRole('alert').filter({ hasText: '동승 정보를 확인하지 못했어요.' }).waitFor();
      assert.deepEqual(await page.evaluate(() => window.__groupSubscriptions), []);
    } finally {
      await page.evaluate(async () => {
        const { stompClient } = await import('/src/shared/ws/stompClient.ts');
        stompClient.subscribe = window.__groupSubscribe;
        window.fetch = window.__groupFetch;
        delete window.__groupSubscribe;
        delete window.__groupFetch;
      });
    }
  });
  await check('채팅에서 탑승 완료·분담액 확인·대화 복귀', async () => {
    await demo();
    await page.getByRole('link', { name: '동승자와 채팅하기' }).click();
    await page.getByLabel('메시지', { exact: true }).fill('정산 보고 다시 올게요');
    await page.getByRole('button', { name: '보내기', exact: true }).click();
    await page.getByRole('button', { name: '탑승했어요', exact: true }).click();
    await page.getByRole('button', { name: '탑승 완료 확인', exact: true }).click();
    await page.waitForURL('**/groups/17/fare');
    await page.getByRole('heading', { name: '동승자별 분담액' }).waitFor();
    await page.getByRole('link', { name: '동승 채팅으로 이동' }).click();
    await page.getByRole('log').getByText('정산 보고 다시 올게요', { exact: true }).waitFor();
    await page.getByRole('link', { name: '탑승 완료 · 정산 금액 보기' }).waitFor();
    await page
      .getByRole('navigation', { name: '주요 메뉴' })
      .getByRole('link', { name: '이력', exact: true })
      .click();
    await page.getByRole('link', { name: '채팅 다시 보기' }).click();
    await page.getByRole('log').getByText('정산 보고 다시 올게요', { exact: true }).waitFor();
    await page.getByRole('link', { name: '이력으로 돌아가기' }).click();
    await page.getByRole('heading', { name: '함께한 이동' }).waitFor();
    const tabs = page.getByRole('navigation', { name: '주요 메뉴' }).getByRole('link');
    assert.deepEqual(await tabs.allTextContents(), ['요청', '대기', '이력', '내 정보']);
    await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      mockState.group.chatExpiresAt = new Date(Date.now() - 1000).toISOString();
    });
    await page.getByRole('link', { name: '채팅 다시 보기' }).click();
    await page
      .getByRole('log')
      .getByText(/채팅 보관 기간이 끝났어요/)
      .waitFor();
    assert.equal(await page.getByLabel('메시지', { exact: true }).count(), 0);
    assert.equal(
      await page.getByRole('log').getByText('정산 보고 다시 올게요', { exact: true }).count(),
      0,
    );
  });
  await check('정산 재조회 실패 시 과거 금액 숨김·재시도', async () => {
    await demo();
    await navigate('/groups/17/fare');
    await page.getByRole('heading', { name: '내 요금, 이렇게 나눴어요' }).waitFor();
    await page.evaluate(() => {
      window.__groupFetch = window.fetch;
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (new URL(url).pathname === '/api/groups/17')
          return Promise.resolve(
            Response.json(
              {
                success: false,
                data: null,
                error: { code: 'INTERNAL_ERROR', message: '정산 확인 테스트 오류' },
              },
              { status: 500 },
            ),
          );
        return window.__groupFetch(input, init);
      };
    });
    try {
      await navigate('/me');
      await navigate('/groups/17/fare');
      await page.getByRole('alert').filter({ hasText: '정산 확인 테스트 오류' }).waitFor();
      assert.equal(
        await page.getByRole('heading', { name: '내 요금, 이렇게 나눴어요' }).count(),
        0,
      );
    } finally {
      await page.evaluate(() => {
        window.fetch = window.__groupFetch;
        delete window.__groupFetch;
      });
    }
    await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await page.getByRole('heading', { name: '내 요금, 이렇게 나눴어요' }).waitFor();
  });
  await check('그룹 이동 뒤 늦은 완료 응답으로 화면 이동 방지', async () => {
    await demo();
    await page.evaluate(() => {
      window.__groupFetch = window.fetch;
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (new URL(url).pathname === '/api/groups/17/complete')
          return new Promise((resolve) => {
            window.__releaseComplete = () =>
              resolve(Response.json({ success: true, data: null, error: null }));
          });
        return window.__groupFetch(input, init);
      };
    });
    try {
      await page.getByRole('button', { name: '탑승 완료', exact: true }).click();
      await page.getByRole('button', { name: '탑승 완료 확인', exact: true }).click();
      await page.waitForFunction(() => typeof window.__releaseComplete === 'function');
      await navigate('/groups/999');
      await page.getByRole('alert').filter({ hasText: '동승 정보를 찾을 수 없어요.' }).waitFor();
      await page.evaluate(() => window.__releaseComplete());
      await page.waitForTimeout(300);
      assert.ok(page.url().endsWith('/groups/999'));
    } finally {
      await page.evaluate(() => {
        window.fetch = window.__groupFetch;
        delete window.__groupFetch;
        delete window.__releaseComplete;
      });
    }
  });
  await check('수락 만료 응답 후 최신 해체 상태 재조회', async () => {
    await demo(true);
    await page.evaluate(() => {
      window.__groupFetch = window.fetch;
      window.fetch = async (input, init) => {
        const url = typeof input === 'string' ? input : input.url;
        if (new URL(url).pathname === '/api/groups/17/accept') {
          const { mockState } = await import('/src/shared/mocks/state.ts');
          mockState.group.status = 'DISSOLVED';
          return Response.json(
            {
              success: false,
              data: null,
              error: { code: 'MATCH_EXPIRED', message: '수락 시간이 지났어요.' },
            },
            { status: 410 },
          );
        }
        return window.__groupFetch(input, init);
      };
    });
    try {
      await page.getByRole('button', { name: '수락', exact: true }).click();
      await page.getByRole('heading', { name: '동승 그룹이 해체됐어요' }).waitFor();
      assert.equal(await page.getByRole('button', { name: '수락', exact: true }).count(), 0);
    } finally {
      await page.evaluate(() => {
        window.fetch = window.__groupFetch;
        delete window.__groupFetch;
      });
    }
  });
  await check('목 시연 체크리스트·기록 내보내기·초기화', async () => {
    await navigate('/dev/demo');
    await page.getByRole('heading', { name: '발표 시연 리허설' }).waitFor();
    await page.getByRole('button', { name: '목 리허설 새로 시작', exact: true }).click();
    await page.getByRole('status').filter({ hasText: '확인한 시연 항목 0/11' }).waitFor();
    assert.equal(await page.getByRole('button', { name: '알림 닫기', exact: true }).count(), 0);
    await page.getByLabel('1. 요청 작성 확인', { exact: true }).check();
    await page.getByRole('status').filter({ hasText: '확인한 시연 항목 1/11' }).waitFor();
    await navigate('/me');
    await navigate('/dev/demo');
    assert.ok(await page.getByLabel('1. 요청 작성 확인', { exact: true }).isChecked());
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: '리허설 기록 다운로드' }).click(),
    ]);
    const report = JSON.parse(await readFile(await download.path(), 'utf8'));
    assert.equal(report.scope, 'MOCK_REHEARSAL');
    assert.equal(report.actualServerVerified, false);
    assert.equal(report.results.filter((item) => item.checked).length, 1);
    assert.equal(report.results.length, 11);
    assert.ok(!JSON.stringify(report).includes('accessToken'));
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: `${output}/rehearsal-checklist.png`, fullPage: true });
    await page.getByRole('button', { name: '목 리허설 새로 시작', exact: true }).click();
    await page.getByRole('status').filter({ hasText: '확인한 시연 항목 0/11' }).waitFor();
    assert.ok(
      await page.evaluate(
        async () =>
          !!(await import('/src/shared/stores/authStore.ts')).useAuthStore.getState().accessToken,
      ),
    );
  });
  await check('시연 버튼으로 요청·대기·수락·정산·채팅·완료 재현', async () => {
    const run = async (name) => {
      await navigate('/dev/demo');
      await page.getByRole('button', { name: `${name} 연습`, exact: true }).click();
    };
    await run('요청 작성');
    await page.waitForURL('**/request');
    await run('대기·취소');
    await page.getByRole('heading', { name: '함께 갈 사람을 찾고 있어요' }).waitFor();
    await page.getByRole('button', { name: '요청 취소', exact: true }).click();
    await page.waitForURL('**/request');
    await run('제안 수락');
    await page.getByRole('heading', { name: '동승 제안이 도착했어요' }).waitFor();
    await page.getByRole('button', { name: '수락', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
    await run('요금 분담');
    await page.getByRole('heading', { name: '내 요금, 이렇게 나눴어요' }).waitFor();
    await run('채팅·신고');
    await page.getByLabel('메시지', { exact: true }).fill('시연 연습 메시지');
    await page.getByRole('button', { name: '보내기', exact: true }).click();
    await page.getByRole('log').getByText('시연 연습 메시지', { exact: true }).waitFor();
    await run('탑승 완료');
    await page.getByRole('button', { name: '탑승 완료', exact: true }).click();
    await page.getByRole('button', { name: '탑승 완료 확인', exact: true }).click();
    await page.waitForURL('**/groups/17/fare');
    await page.getByRole('link', { name: '시연 체크리스트로 돌아가기' }).click();
    await page.getByRole('status').filter({ hasText: '확인한 시연 항목 0/11' }).waitFor();
  });
  await check('시연 버튼으로 거절·재계산·점선·오류 복구·목 갱신 재현', async () => {
    const run = async (name) => {
      await navigate('/dev/demo');
      await page.getByRole('button', { name: `${name} 연습`, exact: true }).click();
    };
    await run('거절·재대기');
    await page.getByRole('button', { name: '거절', exact: true }).click();
    await page.waitForURL('**/waiting');
    await page.getByRole('heading', { name: '함께 갈 사람을 찾고 있어요' }).waitFor();
    await run('재계산·재동의');
    await page.getByRole('heading', { name: '동승 제안이 도착했어요' }).waitFor();
    await page.getByRole('button', { name: '목 재계산 알림 보내기' }).click();
    await page.getByText('7,895원 → 9,600원', { exact: true }).waitFor();
    assert.ok(
      await page.evaluate(async () => {
        const { mockState } = await import('/src/shared/mocks/state.ts');
        return (
          mockState.group.members.reduce((sum, member) => sum + member.shareAmount, 0) ===
          mockState.group.totalFare
        );
      }),
    );
    await run('추정 경로');
    await waitMapStyle('dash');
    await page.getByText('추정 경로예요. 실제 도로와 이동 시간은 다를 수 있어요.').waitFor();
    await run('오류·재시도');
    await page
      .getByRole('main')
      .getByRole('alert')
      .filter({ hasText: '일시적으로 정보를 불러오지 못했어요.' })
      .waitFor();
    await page.getByRole('main').getByRole('button', { name: '다시 시도', exact: true }).click();
    await page.getByRole('heading', { name: '동승이 성사됐어요!' }).waitFor();
    await run('목 세션 갱신');
    await page.getByRole('heading', { name: '내 정보', exact: true }).waitFor();
    const token = await page.evaluate(
      async () =>
        (await import('/src/shared/stores/authStore.ts')).useAuthStore.getState().accessToken,
    );
    assert.ok(token.startsWith('mock-access-'));
    await navigate('/dev/demo');
    await page.getByRole('button', { name: '목 리허설 새로 시작', exact: true }).click();
  });
  await check('401 동시 요청: refresh 한 번·원요청 재시도', async () => {
    const result = await page.evaluate(async () => {
      const { api } = await import('/src/shared/api/client.ts');
      const { useAuthStore } = await import('/src/shared/stores/authStore.ts');
      const user = useAuthStore.getState().user;
      useAuthStore.getState().setTokens('expired', 'mock-refresh-1', user);
      const original = window.fetch;
      let count = 0;
      window.fetch = async (...args) => {
        const url = typeof args[0] === 'string' ? args[0] : args[0].url;
        if (url.endsWith('/api/auth/refresh')) count++;
        return original(...args);
      };
      try {
        const results = await Promise.all([
          api.GET('/api/users/me'),
          api.GET('/api/history'),
          api.GET('/api/stats/me'),
        ]);
        return { count, ok: results.every((r) => r.response.ok) };
      } finally {
        window.fetch = original;
      }
    });
    assert.equal(result.count, 1);
    assert.ok(result.ok);
  });
  await check('refresh 중 로그아웃 시 세션 복원 방지', async () => {
    const result = await page.evaluate(async () => {
      const { api } = await import('/src/shared/api/client.ts');
      const { useAuthStore } = await import('/src/shared/stores/authStore.ts');
      const user = useAuthStore.getState().user;
      useAuthStore.getState().setTokens('expired', 'mock-refresh-2', user);
      const original = window.fetch;
      window.fetch = async (...args) => {
        const url = typeof args[0] === 'string' ? args[0] : args[0].url;
        if (url.endsWith('/api/auth/refresh')) useAuthStore.getState().logout();
        return original(...args);
      };
      try {
        await api.GET('/api/users/me').catch(() => null);
        return useAuthStore.getState().accessToken;
      } finally {
        window.fetch = original;
      }
    });
    assert.equal(result, null);
    await page.waitForURL('**/login');
  });
  await check('갱신 실패 시 로그아웃·로그인 이동', async () => {
    await page.getByRole('button', { name: '목 데이터로 둘러보기' }).click();
    await page.waitForURL(/\/(request|waiting)$/);
    await page.evaluate(async () => {
      const { api } = await import('/src/shared/api/client.ts');
      const { useAuthStore } = await import('/src/shared/stores/authStore.ts');
      useAuthStore.getState().setTokens('expired', 'invalid-refresh', useAuthStore.getState().user);
      await api.GET('/api/users/me').catch(() => null);
    });
    await page.waitForURL('**/login');
  });
  assert.deepEqual(errors, []);
  await page.getByRole('button', { name: '목 데이터로 둘러보기' }).click();
  await page.waitForURL(/\/(request|waiting)$/);
  await demo();
  await page.screenshot({ path: `${output}/group-360.png`, fullPage: true });
  await writeFile(
    `${output}/results.json`,
    JSON.stringify({ checks, errors, date: new Date().toISOString() }, null, 2),
  );
} catch (error) {
  await page.screenshot({ path: `${output}/failure.png`, fullPage: true });
  throw error;
} finally {
  await browser.close();
  server.kill();
}
