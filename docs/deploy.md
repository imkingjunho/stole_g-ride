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
| `docs/schema.sql` 최신화 | 이승민 | 엔티티가 바뀌었으면 T3-2 절차로 다시 뽑는다 |

## 1. 서버에서 처음 한 번

```bash
git clone https://github.com/imkingjunho/stole_g-ride.git && cd stole_g-ride
cp .env.example .env
```

`.env` 에서 **반드시 채울 것** 7개: `BETA_DOMAIN`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, `KAKAO_REST_API_KEY`,
`MAIL_USERNAME`, `MAIL_PASSWORD`. 비어 있으면 compose 가 기동을 거절한다. **단 예시값(`gachiga`·`root`)을 바꿨는지는 거절하지
못한다** — 새 비밀번호로 바꿨는지 눈으로 확인한다. 값에 `$` 가 들어가면 `$$` 로 쓴다.

**⚠️ 인증(임승현 T1-5 `SecurityConfig`)이 main 에 들어오기 전에는 공개 도메인에 띄우지 않는다.** 그 전의 prod 는 Spring 기본 보안으로
뜨는데, 컨테이너 로그에 `Using generated security password: ...` 가 찍히고 그 비밀번호로 모든 API 를 부를 수 있다. 로그를 공유하지 않는다.

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

확인: `https://BETA_DOMAIN/` 에 프론트가 뜨고, `https://BETA_DOMAIN/api/requests/me` 가 JSON(`ApiResponse`)을 돌려준다
(인증이 붙기 전에는 401 — 임승현 T1-5 이후 토큰으로 확인). 이미지 태그는 Redis·Caddy 는 마이너까지(`redis:7.4-alpine`, `caddy:2.11-alpine`), MySQL 은 `mysql:8.0` 으로 고정돼 있어
`--build`·`pull` 때 패치 버전만 올라간다. 더 못 박고 싶으면 `docker compose -f docker-compose.prod.yml images` 로 다이제스트를 확인해 `@sha256:` 으로 고정한다. MySQL 8.0 계열은 지원 종료가 가까우니 8.4 LTS 전환은 팀에 [계약 변경 요청]으로 올린다
(`docs/schema.sql` 재추출 필요).

## 2. 코드가 바뀌었을 때

```bash
git pull --rebase origin main
(cd frontend && VITE_API_MODE=real VITE_API_BASE_URL=https://... VITE_KAKAO_JS_KEY=... npm run build)   # 프론트가 바뀐 경우
docker compose -f docker-compose.prod.yml up -d --build backend                    # 백엔드가 바뀐 경우 (무중단 아님, 몇 초 끊김)
docker image prune -f                                                              # 이전 이미지 정리 — 안 하면 디스크가 찬다
```

**엔티티가 바뀐 경우**는 `validate` 가 기동을 거절한다. 그게 의도다. 순서:
1. 바꾼 사람이 local 에서 `update` 로 반영 → 이승민이 `docs/schema.sql` 을 다시 뽑아 push (T3-2 절차, `docs/schema.sql` 머리말).
2. 서버 DB 에 차이만 `ALTER TABLE` 로 적용한다 (initdb 스크립트는 첫 기동에만 돌기 때문). 컬럼 추가는 안전하고, 삭제·이름 변경은 백업 후.
   비밀번호는 컨테이너 안의 환경변수로 넘긴다 — `-p` 만 쓰면 `-T`(TTY 없음)에서 stdin 첫 줄을 비밀번호로 읽어 실패한다.
   ```bash
   docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --no-data gachiga' > /tmp/before.sql   # 비교용
   docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gachiga' < alter.sql
   ```
   비교할 때 `CHARACTER SET utf8mb4` 표기가 컬럼마다 붙어 있는 차이는 무시한다(schema.sql 로 만든 DB 의 덤프 형식 차이).
3. 백엔드 재기동.

## 3. 되돌리기

```bash
git log --oneline -5                       # 문제 커밋 확인
git revert --no-edit <문제 커밋>            # 이력을 남기고 되돌린다. 트리가 깨끗해서 다음 pull --rebase 도 그대로 된다
(cd frontend && VITE_API_MODE=real VITE_API_BASE_URL=https://... VITE_KAKAO_JS_KEY=... npm run build)   # 프론트가 포함된 커밋이면
docker compose -f docker-compose.prod.yml up -d --build backend
```
`git checkout <커밋> -- 경로` 로 되돌리면 인덱스가 더러워져 다음 `pull --rebase` 가 거절되고, 문제 커밋이 새로 만든 파일은 남는다 — 쓰지 않는다.
DB 는 되돌리지 않는다(추가된 컬럼은 옛 코드가 무시한다). 삭제·이름 변경을 되돌려야 하면 2번의 백업으로.

## 4. 백업

```bash
docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction gachiga' | gzip > backup-$(date +%F).sql.gz
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
