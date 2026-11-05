# 베타 배포 절차 (T3-3)

서버 1대에 `docker compose` 로 MySQL·Redis·백엔드·Caddy 를 띄운다. Caddy 가 HTTPS 를 받고, 정적 프론트(`frontend/dist`)를
서빙하며, `/api/*`·`/ws` 만 백엔드로 넘긴다. 프론트와 API 가 **같은 도메인**이라 CORS 출처는 하나다.

파일: `docker-compose.prod.yml` · `backend/Dockerfile` · `deploy/Caddyfile` · `docs/schema.sql` · `.env.example`

## 0. 필요한 것

| 항목 | 누가 | 비고 |
|---|---|---|
| 서버 1대 (Ubuntu, 2 vCPU · 4GB 이상), Docker + Compose v2 | 이승민 | 학교 서버·클라우드 무료 티어·팀원 노트북 모두 가능 |
| 도메인 1개, A 레코드 → 서버 IP, 80·443 인바운드 개방 | 이승민 | 없으면 아래 "터널" 참고 |
| 카카오 개발자 콘솔에 배포 도메인 등록 (JS 키 도메인 제한) | 송준호 | 등록 전에는 지도가 안 뜬다 |
| Gmail 앱 비밀번호 (인증 메일 발송) | 임승현 | `.env` 의 MAIL_* |
| JWT 서명 키 | 이승민 | `.env` 의 `JWT_SECRET`, 32바이트 이상 무작위 문자열 |
| `docs/schema.sql` 최신화 | 이승민 | 엔티티가 바뀌었으면 T3-2 절차로 다시 뽑는다 |

## 1. 서버에서 처음 한 번

```bash
git clone https://github.com/imkingjunho/stole_g-ride.git && cd stole_g-ride
cp .env.example .env
```

`.env` 에서 **반드시 채울 것** 8개: `BETA_DOMAIN`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, `KAKAO_REST_API_KEY`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `JWT_SECRET`. 비어 있으면 compose 가 기동을 거절한다. **단 예시값(`gachiga`·`root`)을 바꿨는지는 거절하지
못한다** — 새 비밀번호로 바꿨는지 눈으로 확인한다. 값에 `$` 가 들어가면 `$$` 로 쓴다.

현재 REST 인증은 `auth/SecurityConfig`의 JWT 방식이다. 회원가입·인증·로그인·refresh 외의 REST API는
`Authorization: Bearer <accessToken>`이 필요하다. `JWT_SECRET`은 배포 Compose가 백엔드 컨테이너에 전달한다.
키는 32바이트 이상으로 생성해 `.env`에만 보관하고, 값이 바뀌면 기존 Access 토큰을 검증할 수 없으므로 임의로 교체하지 않는다.

STOMP CONNECT용 `contract.auth.AccessTokenVerifier` 계약은 제공됐다(PRD §14.1). 실제 `auth` 검증 구현·`realtime` 연동은
임승현의 후속 작업이며 `DevStompUserInterceptor`는 local/test에서만 동작한다. REST 로그인 성공만으로 운영 채팅·실시간
알림까지 검증됐다고 판단하지 않는다. 실제 STOMP 인증 구현 후 별도로 확인한다.

프론트를 빌드한다 (Node 24). `VITE_` 값은 **빌드 시점**에 박히므로 여기서 넣는다. `frontend/.env` 는 로컬 개발용이라 건드리지 않는다.

```bash
cd frontend && npm ci
VITE_API_MODE=real VITE_API_BASE_URL=https://$(grep ^BETA_DOMAIN ../.env | cut -d= -f2) VITE_KAKAO_JS_KEY=<JS키> npm run build
cd ..
```

띄운다. 첫 기동에서 MySQL 이 `docs/schema.sql` 을 실행해 테이블을 만들고, 백엔드는 `validate` 로 구조만 대조한다.

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps        # 전부 running / healthy 여야 한다
docker compose -f docker-compose.prod.yml logs -f backend   # "Started GachigaApplication" 확인
```

확인: `https://BETA_DOMAIN/`에 프론트가 뜨는지 확인하고 다음 REST 인증 흐름을 점검한다.

1. 토큰 없이 `GET /api/requests/me` 요청 시 401과 `ApiResponse` 오류가 반환된다.
2. 실제 메일로 가입·인증한 뒤 로그인하면 Access/Refresh 토큰이 발급된다.
3. Access 토큰으로 `GET /api/requests/me` 요청 시 200과 `ApiResponse`가 반환된다(진행 중 요청이 없으면 `data: null`).
4. `POST /api/auth/refresh`로 새 토큰을 발급하고 이전 Refresh 토큰의 재사용이 거절되는지 확인한다.

이미지 태그는 Redis·Caddy 는 마이너까지(`redis:7.4-alpine`, `caddy:2.11-alpine`), MySQL 은 `mysql:8.0` 으로 고정돼 있어
`--build`·`pull` 때 패치 버전만 올라간다. 더 못 박고 싶으면 `docker compose -f docker-compose.prod.yml images` 로 다이제스트를 확인해 `@sha256:` 으로 고정한다. MySQL 8.0 계열은 지원 종료가 가까우니 8.4 LTS 전환은 팀에 [계약 변경 요청]으로 올린다
(`docs/schema.sql` 재추출 필요).

## 2. 코드가 바뀌었을 때

```bash
git pull --rebase origin main
(cd frontend && VITE_API_MODE=real VITE_API_BASE_URL=https://... VITE_KAKAO_JS_KEY=... npm run build)   # 프론트가 바뀐 경우
docker compose -f docker-compose.prod.yml up -d --build backend                    # 백엔드가 바뀐 경우 (무중단 아님, 몇 초 끊김)
docker image prune -f                                                              # 이전 이미지 정리 — 안 하면 디스크가 찬다
```

**엔티티가 바뀐 경우**는 `validate`가 기동을 거절한다. initdb 스크립트는 빈 데이터 디렉터리의 첫 기동에만 실행되므로,
`docs/schema.sql`을 바꾸고 컨테이너를 재시작하는 것만으로 기존 DB가 갱신되지는 않는다.

1. 최신 앱을 **별도 빈 검증 DB**에 local(`ddl-auto: update`)로 띄운다. 기존 개발·운영 볼륨을 지울 필요는 없다.
2. 실제 DDL을 아래 스크립트로 추출한다. 컨테이너 안의 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`를 사용하며 데이터는 포함하지 않는다.
   ```bash
   bash deploy/scripts/export-schema.sh <검증용-MySQL-컨테이너> docs/schema.sql
   # 별도 DB 이름을 썼다면 마지막 인자로 지정한다.
   bash deploy/scripts/check-schema-indexes.sh <검증용-MySQL-컨테이너> <DB이름>
   ```
3. 새 빈 DB에 `docs/schema.sql`을 적용해 prod(`validate`) 기동과 인덱스 검사를 확인한다. Hibernate `validate`만으로는
   UNIQUE·조회 인덱스까지 확인되지 않으므로 `check-schema-indexes.sh`를 함께 실행한다.
4. 기존 DB는 백업(§4) 후 이전 스키마와의 차이만 적용한다. 추가 DDL도 잠금·기존 데이터 제약을 확인하고 점검 시간에 실행한다.
5. 변경 후 인덱스 검사 → 백엔드 재기동 → REST 인증·요청 생성/취소를 확인한다.

### 2026-10-28 스키마 보완 적용

이전 4개 테이블(`users`, `reports`, `chat_messages`, `ride_requests`)에서 `hubs`, `refresh_tokens`가 빠져 있었다.
신규 DB는 갱신된 `docs/schema.sql`을 사용한다. 기존 4개 테이블 DB에는 **두 테이블이 모두 없는지 확인하고** 다음 변경분만 적용한다.
한 테이블이라도 이미 있거나 다른 변경이 포함된 DB에는 그대로 재실행하지 말고 실제 DDL을 비교한다.

```bash
docker compose -f docker-compose.prod.yml exec -T mysql sh -eu -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SHOW TABLES"'
# §4 백업을 완료한 뒤, 두 테이블이 없는 이전 스키마에만 실행한다.
docker compose -f docker-compose.prod.yml exec -T mysql sh -eu -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' < docs/schema-upgrade-20261028.sql
bash deploy/scripts/check-schema-indexes.sh "$(docker compose -f docker-compose.prod.yml ps -q mysql)"
docker compose -f docker-compose.prod.yml up -d --build backend
```

변경분은 테이블 추가만 하며 기존 4개 테이블이나 데이터를 삭제하지 않는다. MySQL DDL은 자동 커밋되므로 중간 실패 시
적용된 테이블을 먼저 확인한다. 검증 결과: MySQL 8.0.46의 새 DB·이전 스키마 업그레이드 모두 6개 테이블과
PK 6개·UNIQUE 4개·조회 인덱스 4개가 일치했다. 격리 로컬 환경의 prod `validate` 기동도 성공했다.
실제 베타 서버 검증과는 구분한다([통합 점검 기록](integration-check.md)).

## 3. 되돌리기

```bash
git log --oneline -5                       # 문제 커밋 확인
git revert --no-edit <문제 커밋>            # 이력을 남기고 되돌린다. 트리가 깨끗해서 다음 pull --rebase 도 그대로 된다
(cd frontend && VITE_API_MODE=real VITE_API_BASE_URL=https://... VITE_KAKAO_JS_KEY=... npm run build)   # 프론트가 포함된 커밋이면
docker compose -f docker-compose.prod.yml up -d --build backend
```
`git checkout <커밋> -- 경로` 로 되돌리면 인덱스가 더러워져 다음 `pull --rebase` 가 거절되고, 문제 커밋이 새로 만든 파일은 남는다 — 쓰지 않는다.
DB 는 되돌리지 않는다(추가된 컬럼은 옛 코드가 무시한다). 삭제·이름 변경을 되돌려야 하면 §4의 전체 데이터 백업을 사용한다.

## 4. 백업

```bash
set -o pipefail
umask 077
docker compose -f docker-compose.prod.yml exec -T mysql sh -eu -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --no-tablespaces "$MYSQL_DATABASE"' | gzip > backup-$(date +%F).sql.gz
```
`--single-transaction` 이 있어야 쓰는 중에도 일관된 스냅샷이 나온다. 비밀번호를 묻지 않으므로 cron 에 그대로 넣을 수 있다.
채팅은 3시간 뒤 지워지고(FR-21) 개인정보는 이메일·닉네임뿐이다. 베타 기간엔 하루 1회면 된다.

## 5. 도메인·서버가 없을 때 — 터널

팀원 노트북에서 `docker compose -f docker-compose.prod.yml up -d` 로 띄우고 `cloudflared tunnel --url http://localhost:80` 같은 터널로
임시 https 주소를 받는다. 이때 `BETA_DOMAIN` 은 터널이 준 주소, Caddy 는 인증서를 받을 수 없으므로 `deploy/Caddyfile` 의 사이트 주소 줄
`{$BETA_DOMAIN} {` 을 `:80 {` 으로 바꿔 평문으로 띄우고 터널이 HTTPS 를 맡긴다. 이 구성에서 Caddy 는 백엔드에 `X-Forwarded-Proto: http` 를
보내므로 리다이렉트·쿠키가 http 기준이 된다 — `reverse_proxy backend:8080 { header_up X-Forwarded-Proto https }` 로 고정하면 된다.
주소가 매번 바뀌므로 카카오 콘솔 도메인 등록이 번거롭다 — 시연 리허설용.

## 6. 문제가 생기면

| 증상 | 원인 | 조치 |
|---|---|---|
| backend 가 `Schema-validation: missing column` 으로 죽음 | 엔티티와 DB 가 다름 | 2번 "엔티티가 바뀐 경우" |
| Caddy 가 인증서를 못 받음 | A 레코드 미반영 / 80·443 막힘 | `dig BETA_DOMAIN`, 방화벽 확인. Let's Encrypt 는 시간당 실패 5회 제한 |
| 프론트는 뜨는데 API 가 CORS 에러 | 프론트를 다른 주소로 열었음 | 반드시 `https://BETA_DOMAIN` 으로 접속. 다른 출처가 필요하면 `.env` 의 `GACHIGA_CORS_ALLOWED_ORIGINS` 에 도메인까지 포함해 쉼표로 적고 `up -d backend`. CORS 헤더 자체는 T1-5 `SecurityConfig` 가 `.cors()` 를 켜야 나간다 |
| 지도가 안 뜸 | 카카오 콘솔에 도메인 미등록 | 송준호에게 JS 키 도메인 추가 요청 |
| `MAIL_PASSWORD` 넣었는데 메일이 안 감 | Gmail 계정 비밀번호를 넣음 | 2단계 인증 후 "앱 비밀번호" 발급 |
| compose 가 `JWT_SECRET` 누락으로 거절하거나 토큰 발급이 서명 키 길이 오류로 실패함 | JWT 키 누락·길이 부족 | `.env`에 32바이트 이상 무작위 `JWT_SECRET`을 설정하고 `up -d backend`로 컨테이너를 다시 만든다 |
