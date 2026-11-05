import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

// 재배포한 실제 URL을 검사한다. 로컬 Vite 성공만으로 Vercel 수정 완료를 판단하지 않는다.
const input = process.argv[2];
if (!input)
  throw new Error('사용법: node scripts/verify-deployment.mjs https://운영주소 [--browser]');
const base = new URL(input);
assert.ok(['https:', 'http:'].includes(base.protocol), 'HTTP(S) 주소를 입력해 주세요.');
assert.equal(base.pathname, '/', '경로를 제외한 운영 도메인을 입력해 주세요.');
assert.ok(
  !base.username && !base.password && !base.search && !base.hash,
  '운영 도메인만 입력해 주세요.',
);
const routes = ['/login', '/request', '/waiting'];
let assetPath;
for (const path of routes) {
  const response = await fetch(new URL(path, base), { signal: AbortSignal.timeout(15000) });
  assert.equal(response.status, 200, `${path}: HTTP ${response.status}`);
  assert.match(response.headers.get('content-type') ?? '', /text\/html/i, `${path}: HTML이 아님`);
  const html = await response.text();
  assert.match(html, /<title>가치가/, `${path}: 앱 HTML이 아님`);
  assert.match(html, /id=["']root["']/, `${path}: React 루트 누락`);
  const script = html.match(/<script[^>]+src=["'](\/assets\/[^"']+\.js)["']/)?.[1];
  assert.ok(script, `${path}: 빌드된 JavaScript 경로 누락`);
  if (assetPath) assert.equal(script, assetPath, '경로마다 서로 다른 앱 빌드가 반환됨');
  assetPath = script;
  console.log(`PASS ${path}: HTTP 200 · 앱 HTML`);
}
const asset = await fetch(new URL(assetPath, base), { signal: AbortSignal.timeout(15000) });
assert.equal(asset.status, 200, '앱 JavaScript 로딩 실패');
assert.match(
  asset.headers.get('content-type') ?? '',
  /javascript/i,
  'JavaScript 대신 fallback HTML이 반환됨',
);
console.log('PASS 빌드 자산: JavaScript 응답');

if (process.argv.includes('--browser')) {
  const require = createRequire(import.meta.url);
  const { chromium } = require(
    process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES
      ? `${process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES}/playwright`
      : 'playwright',
  );
  const browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  try {
    for (const path of ['/request', '/waiting']) {
      const context = await browser.newContext({ viewport: { width: 360, height: 800 } });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await page.goto(new URL(path, base).href);
      await page.waitForURL('**/login', { timeout: 15000 });
      await page.getByRole('button', { name: '로그인', exact: true }).waitFor();
      await page.reload();
      await page.getByRole('button', { name: '로그인', exact: true }).waitFor();
      assert.deepEqual(errors, [], `${path}: 브라우저 실행 오류`);
      console.log(`PASS ${path}: 비로그인 로그인 화면 렌더 · 새로고침`);
      await context.close();
    }
  } finally {
    await browser.close();
  }
}
