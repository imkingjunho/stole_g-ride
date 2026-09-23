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

`.env` 에서 **반드시 채울 것**: `BETA_DOMAIN`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, `KAKAO_REST_API_KEY`,
`MAIL_USERNAME`, `MAIL_PASSWORD`. 비밀번호는 로컬 기본값(`gachiga`)을 그대로 두지 않는다. compose 가 빈 값이면 기동을 거절한다.

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
(인증이 붙기 전에는 prod 프로파일에서 Spring 기본 보안이 켜져 401 이 난다 — 임승현 T1-5 이후 토큰으로 확인).

## 2. 코드가 바뀌었을 때

```bash
git pull --rebase origin main
(cd frontend && VITE_API_MODE=real VITE_API_BASE_URL=https://... npm run build)   # 프론트가 바뀐 경우
docker compose -f docker-compose.prod.yml up -d --build backend                    # 백엔드가 바뀐 경우 (무중단 아님, 몇 초 끊김)
```

**엔티티가 바뀐 경우**는 `validate` 가 기동을 거절한다. 그게 의도다. 순서:
1. 바꾼 사람이 local 에서 `update` 로 반영 → 이승민이 `docs/schema.sql` 을 다시 뽑아 push (T3-2 절차, `docs/schema.sql` 머리말).
2. 서버 DB 에 차이만 `ALTER TABLE` 로 적용한다 (initdb 스크립트는 첫 기동에만 돌기 때문). 컬럼 추가는 안전하고, 삭제·이름 변경은 백업 후.
   ```bash
   docker compose -f docker-compose.prod.yml exec mysql mysqldump -u root -p --no-data gachiga > /tmp/before.sql   # 비교용
   docker compose -f docker-compose.prod.yml exec -T mysql mysql -u root -p gachiga < alter.sql
   ```
3. 백엔드 재기동.

## 3. 되돌리기

```bash
git log --oneline -5                       # 직전 커밋 확인
git checkout <직전 커밋> -- backend frontend
docker compose -f docker-compose.prod.yml up -d --build backend
```
DB 는 되돌리지 않는다(추가된 컬럼은 옛 코드가 무시한다). 삭제·이름 변경을 되돌려야 하면 2번의 백업으로.

## 4. 백업

```bash
docker compose -f docker-compose.prod.yml exec mysql mysqldump -u root -p gachiga | gzip > backup-$(date +%F).sql.gz
```
채팅은 3시간 뒤 지워지고(FR-21) 개인정보는 이메일·닉네임뿐이다. 베타 기간엔 하루 1회면 된다.

## 5. 도메인·서버가 없을 때 — 터널

팀원 노트북에서 `docker compose -f docker-compose.prod.yml up -d` 로 띄우고 `cloudflared tunnel --url http://localhost:80` 같은 터널로
임시 https 주소를 받는다. 이때 `BETA_DOMAIN` 은 터널이 준 주소, Caddy 는 인증서를 받을 수 없으므로 `deploy/Caddyfile` 첫 줄을
`:80` 으로 바꿔 평문으로 띄우고 터널이 HTTPS 를 맡긴다. 주소가 매번 바뀌므로 카카오 콘솔 도메인 등록이 번거롭다 — 시연 리허설용.

## 6. 문제가 생기면

| 증상 | 원인 | 조치 |
|---|---|---|
| backend 가 `Schema-validation: missing column` 으로 죽음 | 엔티티와 DB 가 다름 | 2번 "엔티티가 바뀐 경우" |
| Caddy 가 인증서를 못 받음 | A 레코드 미반영 / 80·443 막힘 | `dig BETA_DOMAIN`, 방화벽 확인. Let's Encrypt 는 시간당 실패 5회 제한 |
| 프론트는 뜨는데 API 가 CORS 에러 | 프론트를 다른 주소로 열었음 | 반드시 `https://BETA_DOMAIN` 으로 접속. 다른 출처가 필요하면 `.env` 의 `GACHIGA_CORS_ALLOWED_ORIGINS` 에 쉼표로 추가 |
| 지도가 안 뜸 | 카카오 콘솔에 도메인 미등록 | 송준호에게 JS 키 도메인 추가 요청 |
| `MAIL_PASSWORD` 넣었는데 메일이 안 감 | Gmail 계정 비밀번호를 넣음 | 2단계 인증 후 "앱 비밀번호" 발급 |
