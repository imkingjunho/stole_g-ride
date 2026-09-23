# 부하 테스트 (T3-1)

`k6` 로 매칭 요청 생성·조회·취소를 동시 200명으로 때린다. 목표는 **P95 < 500ms** (PRD §10).
결과는 `docs/performance.md` 에 적는다.

## 준비

1. `brew install k6`
2. Docker 로 MySQL·Redis 를 띄운다: `docker compose up -d --wait`
3. **측정용 백엔드를 8081 에 조용한 로그로** 띄운다. local 프로파일 기본 로그(SQL TRACE)는 지연을 몇 배로 부풀린다.
   ```bash
   cd backend && SERVER_PORT=8081 ./gradlew bootRun --args='--spring.jpa.show-sql=false --logging.level.com.gachiga=INFO --logging.level.org.hibernate.orm.jdbc.bind=WARN'
   ```
4. 사용자 200명이 필요하다 (1인 1건 제한 때문에 VU 마다 다른 사용자). 가입 API 가 아직 없으니 로컬 DB 에 시드한다:
   ```bash
   docker compose exec -T mysql sh -c 'mysql -ugachiga -pgachiga gachiga' < load/seed-users.sql
   ```
   id 11~210 에 `load011@jnu.ac.kr`~ 이 들어간다. 이미 있으면 건너뛴다(`insert ignore`).

## 실행

```bash
k6 run load/k6/ride-requests.js
```

옵션: `-e BASE_URL=... -e VUS=200 -e HOLD=60s -e FIRST_USER_ID=11`

끝나면 `load/k6/last-result.json` 에 요약이 남는다(Git 에 올리지 않는다).

## 시나리오

각 VU(=사용자) 가 반복: `POST /api/requests` → `GET /api/requests/me` ×3 (0.3초 간격, 대기 화면 폴링) → `DELETE /api/requests/{id}`.
30초 동안 200명까지 올리고 60초 유지, 10초 내린다. 생성은 201 또는 409(직전 회차 요청이 아직 살아 있을 때)를 정상으로 본다.

인증은 Phase 0 방식(`X-Dev-User` 헤더)이다. JWT(T1-5)가 붙으면 `headers()` 를 토큰으로 바꿔야 한다.
