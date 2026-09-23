# 성능 기록 (T3-1 · T3-4)

측정한 것만 적는다. 숫자마다 어떻게 측정했는지와 재현 명령을 같이 둔다.

## 1. REST 부하 — 동시 200명 (2026-09-23)

**결론: 목표 P95 < 500ms(PRD §10) 를 모든 엔드포인트가 10배 이상 여유로 만족한다.** 실패 0건.

| 엔드포인트 | 평균 | 중앙값 | P90 | P95 | P99 | 최대 |
|---|---|---|---|---|---|---|
| `POST /api/requests` (생성) | 18.7ms | 13.9ms | 29.5ms | **45.2ms** | 110.6ms | 270.6ms |
| `GET /api/requests/me` (대기 화면 폴링) | 5.4ms | 4.2ms | 8.3ms | **11.4ms** | 27.5ms | 131.3ms |
| `DELETE /api/requests/{id}` (취소) | 7.2ms | 5.9ms | 11.0ms | **14.2ms** | 31.1ms | 179.6ms |

- 총 55,885 요청 / 100초, **551 req/s**, 11,177 회차. HTTP 실패 0.00%, 체크 100%.
- 생성이 가장 느린 이유: 사용자 조회(UserPort) + 거점 조회 + 거리 검증 + 단독 경로 계산(EstimatedRouteProvider, 카카오 미연동) + INSERT + 커밋 후 Redis ZADD. 카카오 길찾기가 붙으면 여기에 외부 호출 왕복(수백 ms)이 더해진다 — 그때는 이 표를 다시 잰다.
- 조회가 빠른 이유: `idx_ride_requests_user_status` 로 사용자별 진행 중 요청 1건 + 거점 대기 인원 COUNT 1건.

**측정 환경** — 결과를 읽을 때 감안할 것
- 부하 발생기(k6 v2.3.0)와 서버가 **같은 노트북**(MacBook Air, Apple Silicon)에서 돌았다. 네트워크 지연이 없고 CPU 를 나눠 쓴다. 실제 서버에서는 왕복 지연이 더해지고 CPU 경합은 줄어든다.
- 서버: Spring Boot 3.2.5 / Java 17, local 프로파일이지만 **SQL 로그를 끈 상태**(`show-sql=false`, bind TRACE 끔). 기본 local 로그(SQL 전부 TRACE 출력)로는 콘솔 I/O 가 지연을 몇 배로 부풀린다.
- DB: Docker MySQL 8.0 · Redis 7 (같은 노트북). HikariCP 기본 풀(10).
- 시나리오: 사용자 200명(id 11~210)이 각자 생성 → 조회 3회(0.3초 간격) → 취소 반복. 30초 램프업, 60초 유지, 10초 램프다운.

**재현**
```bash
docker compose up -d --wait
docker compose exec -T mysql sh -c 'mysql -ugachiga -pgachiga gachiga' < load/seed-users.sql
cd backend && SERVER_PORT=8081 ./gradlew bootRun --args='--spring.jpa.show-sql=false --logging.level.com.gachiga=INFO --logging.level.org.hibernate.orm.jdbc.bind=WARN'
k6 run load/k6/ride-requests.js        # 다른 터미널
```
자세한 것은 `load/README.md`.

## 2. 개선 전후 — 쿼리 수 (T2-3, 2026-09-23)

대기 상태 push(`/user/queue/status`, 5초 주기)가 연결된 사용자마다 `QueueStatusPort.statusOf` 를 불렀다.

| 연결 사용자 | 개선 전 (사람마다 `statusOf`) | 개선 후 (`statusOfAll` 한 번) |
|---|---|---|
| 1명 | SQL 2 | SQL 2 |
| 10명 | SQL 20 | SQL 2 |
| 50명 | SQL 100 | SQL 2 |
| 200명 (목표 동시 접속) | **SQL 400 / 5초 ≈ 80 SQL/s 상시** | **SQL 2 / 5초** |
| 1,001명 | SQL 2,002 | SQL 4 (500명 단위 IN 분할) |

- 측정: H2 인메모리에서 Hibernate 통계(`getPrepareStatementCount`)로 셌다. `backend/src/test/java/com/gachiga/ride/QueueStatusBatchTest.java` 가 이 표를 테스트로 고정한다 — 숫자가 바뀌면 빌드가 깨진다.
- 값은 사람마다 부른 것과 동일함을 같은 테스트가 확인한다(대기·매칭·확정·요청 없음·거점 여러 곳).
- **realtime 쪽 호출부 교체(임승현)가 끝나야 운영에서 효과가 난다.** 계약과 구현은 올라갔다.

## 3. Redis 장애 시 응답 지연 (Phase 1, 2026-09-21)

요청 생성은 커밋 뒤 같은 스레드에서 Redis 대기열에 넣는다. Redis 가 죽어 있을 때:

| | 응답 시간 |
|---|---|
| 개선 전 (Lettuce 기본 타임아웃 60초) | **20초 이상** — 이미 저장된 요청의 응답이 Redis 를 기다리며 멈춤 |
| 개선 후 (`spring.data.redis.timeout: 1s`, `connect-timeout: 1s`) | **1.04초** |

- 대기열은 색인일 뿐이라 실패해도 요청은 유효하고, 어긋난 것은 기동 시 `RideQueueInitializer` 가 DB 기준으로 다시 만든다.
- 측정: 로컬에서 `docker compose stop redis` 후 `POST /api/requests` 를 `curl -w %{time_total}` 로.

## 4. 다음에 잴 것

- 카카오 길찾기 연동(송준호) 뒤 생성 P95 — 외부 호출이 병목이 될 가능성이 크다. 그때 경로 캐시(`route:{좌표해시}`, PRD §7.1) 적중률과 함께.
- JWT 인증(임승현 T1-5) 뒤 같은 시나리오 — 토큰 검증 비용.
- WebSocket 200 연결 유지 + 5초 push — 위 2절의 SQL 2개가 실제 운영 경로에서도 나오는지.
- 실제 베타 서버(`docs/deploy.md`)에서 한 번 — 네트워크 왕복 포함.
