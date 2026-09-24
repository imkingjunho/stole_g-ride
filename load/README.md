# 부하 테스트 (T3-1)

`k6` 로 매칭 요청 생성·조회·취소를 동시 200명으로 때린다. 목표는 **P95 < 500ms** (PRD §10).
결과는 `docs/performance.md` 에 적는다.

## 준비

1. `brew install k6`
2. Docker 로 MySQL·Redis 를 띄운다: `docker compose up -d --wait`
3. **측정용 백엔드를 8081 에 따로** 띄운다(개발 서버와 분리). SQL 로그는 끄는 편이 낫다 — 지연에는 영향이 없었지만(`docs/performance.md` §1) 100초에 200MB 가 쌓인다.
   ```bash
   cd backend && SERVER_PORT=8081 ./gradlew bootRun --args='--spring.jpa.show-sql=false --logging.level.com.gachiga=INFO --logging.level.org.hibernate.orm.jdbc.bind=WARN'
   ```
4. 사용자 200명이 필요하다 (1인 1건 제한 때문에 VU 마다 다른 사용자). 가입 API 가 아직 없으니 로컬 DB 에 시드한다:
   ```bash
   docker compose exec -T mysql sh -c 'mysql -ugachiga -pgachiga gachiga' < load/seed-users.sql
   ```
   **id 11~210 으로 고정**해서 넣는다(`load011@jnu.ac.kr`~). 이미 있으면 건너뛴다. k6 는 시작할 때 첫·마지막 사용자가 있는지 확인하고 없으면 멈춘다.
5. **8080 개발 서버는 내린다.** 측정 서버(8081)가 같은 Redis 대기열(`queue:*`)을 기동 시 다시 만들고, 같은 DB 에 회차마다 CANCELLED 행을 남긴다.
   끝난 뒤 정리: `delete from ride_requests where dest_name like '부하 목적지 %';`

## 실행

**저장소 루트에서** 실행한다 (요약 파일 경로가 루트 기준이다):
```bash
k6 run load/k6/ride-requests.js
```

옵션: `-e BASE_URL=... -e VUS=200 -e HOLD=60s -e FIRST_USER_ID=11 -e RESULT_FILE=load/k6/last-result.json`

끝나면 요약이 `load/k6/last-result.json` 에 남는다(Git 에 올리지 않는다). `create_409` 가 0 이 아니면 이전 실행의 잔여 요청이 있었다는 뜻이다 —
정리하고 다시 돈다. 그 실행의 create 수치는 거절이 섞여 실제보다 좋게 나온다.

## 시나리오

각 VU(=사용자) 가 반복: `POST /api/requests` → `GET /api/requests/me` ×3 (0.3초 간격, 대기 화면 폴링) → `DELETE /api/requests/{id}`.
30초 동안 200명까지 올리고 60초 유지, 10초 내린다. 생성은 201 만 정상이다. 409 는 중단된 이전 실행이 남긴 요청이 있을 때만 나오며 따로 센다.

인증은 Phase 0 방식(`X-Dev-User` 헤더)이다. JWT(T1-5)가 붙으면 `headers()` 를 토큰으로 바꿔야 한다.
