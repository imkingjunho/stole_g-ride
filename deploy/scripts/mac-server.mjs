// Mac 임시 공개 실행 도구. 애플리케이션은 기존 Java 백엔드·React 빌드를 사용한다.
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync, openSync, closeSync, cpSync } from 'node:fs';
import { homedir } from 'node:os';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';
import { createServer } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../../', import.meta.url));
const work = join(root, '.local-server');
const stateFile = join(work, 'state.json');
const container = 'gachiga-mac-web';
const action = process.argv[2] ?? 'status';
const readEnv = (file) => existsSync(file) ? parseEnv(readFileSync(file, 'utf8')) : {};
const readState = () => existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8')) : {};
let state = readState();
const save = () => writeFileSync(stateFile, JSON.stringify(state, null, 2), { mode: 0o600 });
const ps = (pid, field) => spawnSync('ps', ['-p', String(pid), '-o', `${field}=`], { encoding: 'utf8' }).stdout?.trim() ?? '';
const alive = (entry) => entry && ps(entry.pid, 'lstart') === entry.started && ps(entry.pid, 'command').includes(entry.marker);

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} 실행 실패. 위 출력과 .local-server 로그를 확인하세요.`);
  return result.stdout;
}

async function freePort(port) {
  await new Promise((accept, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`${port} 포트가 사용 중입니다. 기존 개발 서버를 종료한 뒤 다시 실행하세요.`)));
    server.listen(port, '0.0.0.0', () => server.close(accept));
  });
}

async function background(name, command, args, env, marker) {
  const fd = openSync(join(work, `${name}.log`), 'w', 0o600);
  const child = spawn(command, args, { cwd: root, env, detached: true, stdio: ['ignore', fd, fd] });
  closeSync(fd);
  await new Promise((accept, reject) => { child.once('spawn', accept); child.once('error', reject); });
  child.unref();
  state[name] = { pid: child.pid, marker, started: ps(child.pid, 'lstart') };
  save();
}

async function waitFor(check, attempts, message) {
  for (let i = 0; i < attempts; i++) {
    if (await check()) return;
    await delay(1000);
  }
  throw new Error(message);
}

async function ready(base) {
  try {
    const response = await fetch(`${base}/api/users/me`, { signal: AbortSignal.timeout(3000) });
    const body = await response.json();
    return response.status === 401 && body.error?.code === 'UNAUTHORIZED';
  } catch { return false; }
}

async function stop() {
  // 저장한 시작 시각과 실행 파일을 대조해 PID가 재사용된 다른 프로세스를 건드리지 않는다.
  if (alive(state.tunnel)) process.kill(state.tunnel.pid, 'SIGTERM');
  if (state.containerId) {
    const info = spawnSync('docker', ['inspect', '--format', '{{.Id}}', container], { encoding: 'utf8' });
    if (info.status === 0 && info.stdout.trim() === state.containerId) run('docker', ['rm', '-f', container]);
  }
  if (alive(state.backend)) process.kill(state.backend.pid, 'SIGTERM');
  await waitFor(() => !alive(state.backend) && !alive(state.tunnel), 20, '종료 대기 시간이 지났습니다. status와 로그를 확인하세요.');
  state = {};
  if (existsSync(work)) save();
  console.log('공개 서버를 종료했습니다. MySQL·Redis와 저장된 데이터는 유지합니다.');
}

async function start() {
  if (process.platform !== 'darwin') throw new Error('이 실행 도구는 Mac용입니다.');
  if (alive(state.backend) || alive(state.tunnel) || state.containerId) {
    throw new Error('이전 공개 서버 기록이 있습니다. status로 확인하고 stop 후 다시 시작하세요.');
  }
  await freePort(8080);
  await freePort(8787);
  const existing = spawnSync('docker', ['inspect', container], { stdio: 'ignore' });
  if (existing.status === 0) throw new Error(`${container} 컨테이너가 이미 있습니다. 소유자를 확인하세요.`);
  mkdirSync(work, { recursive: true, mode: 0o700 });
  const envFile = join(root, '.env.mac.local');
  const env = { ...process.env, ...readEnv(join(root, '.env')), ...readEnv(envFile) };
  if (!env.JWT_SECRET) {
    env.JWT_SECRET = randomBytes(48).toString('base64url');
    const previous = existsSync(envFile) ? readFileSync(envFile, 'utf8') : '# Mac 공개 서버 전용. Git에 올리지 않는다.\n';
    writeFileSync(envFile, `${previous}\nJWT_SECRET=${env.JWT_SECRET}\n`, { mode: 0o600 });
  }
  if (Buffer.byteLength(env.JWT_SECRET) < 32) throw new Error('JWT_SECRET은 32바이트 이상이어야 합니다.');
  const cloudflared = env.CLOUDFLARED_BIN || (existsSync(join(homedir(), '.local/bin/cloudflared'))
    ? join(homedir(), '.local/bin/cloudflared') : 'cloudflared');
  run(cloudflared, ['--version']);
  const javaHome = run('/usr/libexec/java_home', ['-v', '17'], { stdio: 'pipe', encoding: 'utf8' }).trim();
  env.JAVA_HOME = javaHome;
  run('docker', ['compose', 'up', '-d', '--wait'], { env });
  run('./gradlew', ['bootJar'], { cwd: join(root, 'backend'), env });
  const jar = join(work, 'backend.jar');
  cpSync(join(root, 'backend/build/libs/gachiga-0.0.1-SNAPSHOT.jar'), jar);

  await background('tunnel', cloudflared,
    ['tunnel', '--no-autoupdate', '--protocol', 'http2', '--url', 'http://127.0.0.1:8787'], process.env,
    'http://127.0.0.1:8787');
  await waitFor(() => {
    const log = readFileSync(join(work, 'tunnel.log'), 'utf8');
    state.url = log.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/)?.[0];
    return Boolean(state.url);
  }, 60, '외부 주소 발급 실패. .local-server/tunnel.log를 확인하세요.');
  save();
  const frontendOrigin = env.MAC_FRONTEND_ORIGIN || 'https://stole-g-ride-bpwm.vercel.app';
  const origins = [state.url, frontendOrigin, 'http://localhost:5173', 'http://127.0.0.1:5173',
    'http://localhost:5174', 'http://127.0.0.1:5174'];
  const backendEnv = {
    ...env, SPRING_PROFILES_ACTIVE: 'prod', SERVER_PORT: '8080',
    DB_HOST: env.DB_HOST === 'localhost' ? '127.0.0.1' : (env.DB_HOST || '127.0.0.1'),
    DB_PORT: env.DB_PORT || '3306', DB_NAME: env.DB_NAME || 'gachiga', DB_USERNAME: env.DB_USERNAME || 'gachiga',
    DB_PASSWORD: env.DB_PASSWORD || 'gachiga', REDIS_HOST: '127.0.0.1', REDIS_PORT: env.REDIS_PORT || '6379',
    REDIS_PASSWORD: env.REDIS_PASSWORD || '', GACHIGA_CORS_ALLOWED_ORIGINS: origins.join(','),
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT: '5000',
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT: '5000', SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT: '5000',
  };
  await background('backend', join(javaHome, 'bin/java'), ['-Xmx1024m', '-jar', jar], backendEnv, jar);
  await waitFor(() => ready('http://127.0.0.1:8080'), 60,
    '백엔드 기동 실패. .local-server/backend.log 확인. prod는 DB 구조를 자동 변경하지 않습니다.');

  const front = readEnv(join(root, 'frontend/.env'));
  // 프론트 빌드에 서버 비밀을 전달하지 않는다. 공개 가능한 VITE_ 값만 별도로 지정한다.
  const buildEnv = { ...process.env, VITE_API_MODE: 'real', VITE_API_BASE_URL: state.url,
    VITE_KAKAO_JS_KEY: front.VITE_KAKAO_JS_KEY || env.KAKAO_JS_KEY || '' };
  run('npm', ['run', 'build'], { cwd: join(root, 'frontend'), env: buildEnv });
  cpSync(join(root, 'frontend/dist'), join(work, 'site'), { recursive: true });
  const id = run('docker', ['run', '-d', '--name', container,
    '--publish', '127.0.0.1:8787:80',
    '--mount', `type=bind,src=${resolve(root, 'deploy/Caddyfile.mac')},dst=/etc/caddy/Caddyfile,readonly`,
    '--mount', `type=bind,src=${join(work, 'site')},dst=/srv/frontend,readonly`,
    '--log-opt', 'max-size=10m', '--log-opt', 'max-file=2',
    'caddy:2-alpine'], { stdio: 'pipe', encoding: 'utf8' }).trim();
  state.containerId = id;
  state.frontendOrigin = frontendOrigin;
  state.mailConfigured = Boolean(env.MAIL_USERNAME && env.MAIL_PASSWORD);
  save();
  await waitFor(() => ready('http://127.0.0.1:8787'), 20, '공개 프록시에서 백엔드 연결에 실패했습니다.');
  console.log(`\n공유할 화면·API 주소: ${state.url}`);
  console.log(`Vercel 소유자에게 전달: VITE_API_MODE=real / VITE_API_BASE_URL=${state.url} (설정 후 재배포)`);
  console.log('카카오 JS 키의 허용 도메인에도 공유 주소를 등록하세요.');
  if (!state.mailConfigured) console.log('주의: MAIL_USERNAME·MAIL_PASSWORD가 없어 신규 가입 인증 메일은 발송되지 않습니다. 기존 계정은 로그인할 수 있습니다.');
  console.log('Mac이 꺼지거나 잠자기에 들어가면 연결이 끊깁니다. 다시 시작하면 공유 주소도 바뀝니다.');
}

try {
  if (action === 'start') {
    // 새로 만든 자원만 실패 시 정리한다. 기존 서버 기록을 확인하다 실패한 경우에는 중지하지 않는다.
    const alreadyManaged = alive(state.backend) || alive(state.tunnel) || state.containerId;
    try { await start(); } catch (error) {
      if (!alreadyManaged && (state.backend || state.tunnel || state.containerId)) await stop();
      throw error;
    }
  } else if (action === 'stop') await stop();
  else if (action === 'status') {
    console.log(JSON.stringify({ url: state.url ?? null, backend: Boolean(alive(state.backend)),
      tunnel: Boolean(alive(state.tunnel)), apiReady: await ready('http://127.0.0.1:8787'),
      mailConfigured: state.mailConfigured ?? false }, null, 2));
  } else throw new Error('사용법: node deploy/scripts/mac-server.mjs start|status|stop');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
