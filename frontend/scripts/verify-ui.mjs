import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
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
  { cwd: root, env: { ...process.env, VITE_API_MODE: 'mock' } },
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
const page = await context.newPage();
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
    await page.waitForURL('**/request');
    await demo();
    await page.getByText('함께 9,700원', { exact: true }).waitFor();
    await page.getByRole('link', { name: '내 요금은 어떻게 계산됐나요? →' }).click();
    await page.getByText('100원 단위·총액 보정: -50원', { exact: true }).waitFor();
    const sums = await page.evaluate(async () => {
      const { mockState } = await import('/src/shared/mocks/state.ts');
      const g = mockState.group;
      return {
        total: g.totalFare,
        sum: g.members.reduce((s, m) => s + m.shareAmount, 0),
        breakdowns: g.members.every(
          (m) =>
            m.breakdown.reduce((s, x) => s + x.share, 0) + m.roundingAdjustment === m.shareAmount,
        ),
      };
    });
    assert.equal(sums.total, sums.sum);
    assert.ok(sums.breakdowns);
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
      mockState.group.members.find((m) => m.isMe).shareAmount = 9600;
      mockState.group.members.forEach((m) => (m.accepted = false));
      notifyMatch('RECALCULATED', '경로가 변경됐어요.');
    });
    await page.getByText('9,700원 → 9,600원', { exact: true }).waitFor();
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
    await page.waitForURL('**/history');
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
    await page.waitForURL('**/request');
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
  await page.waitForURL('**/request');
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
