// 서버 터미널에서 전용 계정 하나를 발급하고 기존 관리자 ID 목록에 추가한다.
import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  writeFileSync,
} from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const target = process.argv[2];
if (!['mac', 'prod'].includes(target)) {
  console.error('사용법: node deploy/scripts/create-admin.mjs mac|prod');
  process.exit(1);
}
process.chdir(fileURLToPath(new URL('../../', import.meta.url)));
const config = target === 'mac' ? '.env.mac.local' : '.env';
const read = (file) => (existsSync(file) ? readFileSync(file, 'utf8') : '');
const original = read(config);
const variable = 'GACHIGA_ADMIN_USER_IDS';
const settings = {
  ...process.env,
  ...parseEnv(read('.env')),
  ...parseEnv(original),
};
const ids = (settings[variable] ?? '')
  .split(',')
  .map((id) => id.trim())
  .filter(Boolean);
if (ids.some((id) => !/^\d+$/.test(id)))
  throw new Error('기존 관리자 ID 설정을 확인해 주세요.');
const compose = [
  'compose',
  '-f',
  target === 'mac' ? 'docker-compose.yml' : 'docker-compose.prod.yml',
];
const docker = (args, input) =>
  execFileSync('docker', [...compose, ...args], {
    env: settings,
    input,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'inherit'],
  }).trim();
const mysql = (sql) =>
  docker(
    [
      'exec',
      '-T',
      'mysql',
      'sh',
      '-c',
      'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -BN -u "$MYSQL_USER" "$MYSQL_DATABASE"',
    ],
    sql,
  );

// ID 권한을 다른 데이터베이스의 동명 회원에게 부여하지 않도록 실제 연결 대상을 대조한다.
if (target === 'mac') {
  const address = docker(['port', 'mysql', '3306']);
  if (
    !['localhost', '127.0.0.1'].includes(settings.DB_HOST || '127.0.0.1') ||
    address !== '127.0.0.1:' + (settings.DB_PORT || '3306')
  )
    throw new Error(
      '백엔드 DB와 로컬 Compose MySQL 주소가 다릅니다. 실행 서버 설정을 확인해 주세요.',
    );
}
if (mysql('SELECT DATABASE()') !== (settings.DB_NAME || 'gachiga'))
  throw new Error('백엔드와 컨테이너의 DB 이름이 다릅니다.');

const email = 'admin@gachiga.invalid';
const secretFile = resolve('.local-server', 'admin-' + target + '.json');
mkdirSync('.local-server', { recursive: true, mode: 0o700 });
if (!existsSync(secretFile)) {
  if (mysql(`SELECT id FROM users WHERE email='${email}'`)) {
    throw new Error(
      '전용 계정이 이미 있습니다. 최초 발급 파일을 사용해 주세요.',
    );
  }
  const password = randomBytes(24).toString('base64url');
  const encoded = execFileSync('htpasswd', ['-niBC', '12', 'admin'], {
    input: password + '\n',
    encoding: 'utf8',
  })
    .trim()
    .slice('admin:'.length);
  writeFileSync(
    secretFile,
    JSON.stringify({ email, password, encoded }, null, 2) + '\n',
    {
      mode: 0o600,
      flag: 'wx',
    },
  );
}
const { encoded } = JSON.parse(readFileSync(secretFile, 'utf8'));
if (!/^\$2[aby]\$12\$[A-Za-z0-9./]{53}$/.test(encoded))
  throw new Error('발급 파일을 확인해 주세요.');
const id = mysql(`
  INSERT INTO users (email, password_hash, nickname, gender, verified_at, created_at, report_count, status)
  SELECT '${email}', '${encoded}', '가치가운영팀', 'M', NOW(), NOW(), 0, 'ACTIVE' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='${email}');
  SELECT id FROM users WHERE email='${email}' AND BINARY password_hash='${encoded}';
`);
if (!/^\d+$/.test(id))
  throw new Error('저장된 계정과 발급 파일이 다릅니다. 권한을 확인해 주세요.');
const setting = variable + '=' + [...new Set([...ids, id])].join(',');
const line = /^(?:export\s+)?GACHIGA_ADMIN_USER_IDS\s*=.*$/gm;
const updated = original.match(line)
  ? original.replace(line, setting)
  : original.trimEnd() + '\n' + setting + '\n';
if (updated !== original) {
  const tmp = config + '.' + process.pid + '.local';
  writeFileSync(tmp, updated, { mode: 0o600, flag: 'wx' });
  renameSync(tmp, config);
}
console.log(
  '관리자 이메일 ' +
    email +
    '\n비밀번호 파일 ' +
    secretFile +
    '\n' +
    config +
    ' 적용 후 백엔드를 다시 시작해 주세요.',
);
