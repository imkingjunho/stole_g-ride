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
const user = {
  id: 41,
  email: 'transport@jnu.ac.kr',
  nickname: '전송검사',
  gender: 'M',
  status: 'ACTIVE',
};
let refreshes = 0;
const backend = createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', 'http://127.0.0.1:5187');
  res.setHeader('Access-Control-Allow-Headers', 'content-type,authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Content-Type', 'application/json');
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  const success = (data) => res.end(JSON.stringify({ success: true, data, error: null }));
  if (req.url === '/api/auth/login')
    return success({
      accessToken: 'expired',
      refreshToken: 'test-refresh',
      accessTokenExpiresIn: 1,
      user,
    });
  if (req.url === '/api/auth/refresh') {
    refreshes++;
    await new Promise((r) => setTimeout(r, 60));
    return success({
      accessToken: 'renewed',
      refreshToken: 'rotated',
      accessTokenExpiresIn: 1800,
      user,
    });
  }
  if (req.headers.authorization !== 'Bearer renewed') {
    res.writeHead(401);
    res.end(
      JSON.stringify({
        success: false,
        data: null,
        error: { code: 'UNAUTHORIZED', message: '만료' },
      }),
    );
    return;
  }
  if (req.url === '/api/users/me') return success(user);
  if (req.url === '/api/stats/me')
    return success({
      totalRides: 9,
      totalSaving: 12300,
      averageSavingRate: 0.2,
      mostUsedHubName: '전남대 후문',
    });
  res.writeHead(404);
  res.end();
});
await new Promise((r) => backend.listen(5188, '127.0.0.1', r));
const server = spawn(
  process.execPath,
  ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5187', '--strictPort'],
  {
    cwd: fileURLToPath(new URL('..', import.meta.url)),
    env: { ...process.env, VITE_API_MODE: 'real', VITE_API_BASE_URL: 'http://127.0.0.1:5188' },
  },
);
await new Promise((resolve, reject) => {
  server.stdout.on('data', (b) => {
    if (b.toString().includes('Local:')) resolve();
  });
  server.on('error', reject);
});
let browser;
try {
  browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-gl=angle', '--use-angle=swiftshader'],
  });
  const page = await browser.newPage();
  await page.goto('http://127.0.0.1:5187/login');
  await page.getByRole('button', { name: '로그인', exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '목 데이터로 둘러보기' }).count(), 0);
  await page.getByLabel('학교 이메일').fill('transport@jnu.ac.kr');
  await page.getByLabel('비밀번호').fill('test-password');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.waitForURL('**/request');
  const values = await page.evaluate(async () => {
    const { api, unwrap } = await import('/src/shared/api/client.ts');
    const responses = await Promise.all([api.GET('/api/users/me'), api.GET('/api/stats/me')]);
    return responses.map((r) => unwrap(r.data, r.error));
  });
  assert.equal(values[0].id, 41);
  assert.equal(values[1].totalSaving, 12300);
  assert.equal(refreshes, 1);
  assert.equal(
    await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length),
    0,
  );
  console.log(
    'PASS real 설정에서 MSW 없이 로컬 HTTP 테스트 서버 요청·401 동시 갱신·새 Bearer 재시도',
  );
} finally {
  await browser?.close();
  server.kill();
  backend.close();
}
