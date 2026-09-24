# 성능 기록 (T3-1 · T3-4)

측정한 것만 적는다. 숫자마다 어떻게 측정했는지와 재현 명령을 같이 둔다.

## 1. REST 부하 — 동시 200명 (2026-09-23)

**결론: 로컬 노트북·인증 없음·카카오 미연동·매칭 tick/WS push 미가동 조건에서 세 엔드포인트 모두 P95 가 목표(500ms)의 1/10 아래다.** 실패 0건.
운영 조건(별도 서버, JWT, 카카오 호출, WebSocket 200연결)에서는 다시 재야 한다 — §4.

| 엔드포인트 | 평균 | 중앙값 | P90 | P95 | P99 | 최대 |
|---|---|---|---|---|---|---|
| `POST /api/requests` (생성) | 15.6ms | 13.9ms | 24.5ms | **29.5ms** | 43.3ms | 182.0ms |
| `GET /api/requests/me` (대기 화면 폴링) | 4.5ms | 3.8ms | 7.6ms | **9.3ms** | 14.5ms | 77.6ms |
| `DELETE /api/requests/{id}` (취소) | 6.4ms | 5.7ms | 10.0ms | **12.0ms** | 18.8ms | 43.2ms |

- 총 56,127 요청, 11,225 회차, **554 req/s**(램프다운 유예 포함 101.3초 기준). HTTP 실패 0.00%, 409 0건 — **생성 11,225건이 전부 201**.
- 생성이 가장 느린 이유: 사용자 조회(UserPort) + 거점 조회 + 거리 검증 + 단독 경로 계산(EstimatedRouteProvider, 카카오 미연동) + INSERT + 커밋 후 Redis ZADD. 카카오 길찾기가 붙으면 여기에 외부 호출 왕복(수백 ms)이 더해진다 — 그때는 이 표를 다시 잰다.
- 조회가 빠른 이유: `idx_ride_requests_user_status` 로 사용자별 진행 중 요청 1건 + 거점 대기 인원 COUNT 1건.

**측정 환경** — 결과를 읽을 때 감안할 것
- 부하 발생기(k6 v2.3.0)와 서버가 **같은 노트북**(MacBook Air, Apple Silicon)에서 돌았다. 네트워크 지연이 없고 CPU 를 나눠 쓴다. 실제 서버에서는 왕복 지연이 더해지고 CPU 경합은 줄어든다. 웜업 없이 램프업부터 셌다.
- 서버: Spring Boot 3.2.5 / Java 17, local 프로파일, SQL 로그 끔(`show-sql=false`, bind WARN). 로그 설정의 영향은 아래 "로그 설정 전후" — 결론은 "없다".
- 빠진 것: JWT 인증(T1-5 전, `X-Dev-User` 헤더), 카카오 길찾기(추정 경로 사용), 매칭 tick(서준 미구현), WebSocket 연결·5초 push(연결 0). 이것들이 붙으면 생성 경로와 DB 부하가 늘어난다.
- DB: Docker MySQL 8.0 · Redis 7 (같은 노트북). HikariCP 기본 풀(10).
- 시나리오: 사용자 200명(id 11~210)이 각자 생성 → 조회 3회(0.3초 간격) → 취소 반복. 30초 램프업, 60초 유지, 10초 램프다운.
- 위 표는 커밋된 `load/k6/ride-requests.js` 그대로 잰 값이다. 그 전 초안 스크립트(DELETE 에 `name` 태그 없음, k6 가 약 10만 시계열을 쥔 상태)로 잰 첫 측정은 생성 P95 45.2ms · 조회 11.4ms · 취소 14.2ms 였다.

**로그 설정 전후** — 같은 시나리오(200명·60초 유지), 커밋된 스크립트, 같은 노트북에서 연속 측정

| 서버 설정 | 생성 P95 | 조회 P95 | 취소 P95 | 처리량 | 실패 |
|---|---|---|---|---|---|
| local 기본 로그 (SQL TRACE + 파라미터 바인딩 출력) | 20.3ms | 6.6ms | 10.2ms | 557 req/s | 0 (409 0) |
| SQL 로그 끔 (`show-sql=false`, bind WARN) | 29.5ms | 9.3ms | 12.0ms | 554 req/s | 0 (409 0) |

**로그를 파일로 보내는 한 SQL TRACE 로그는 지연에 측정 가능한 영향을 주지 않았다.** 오히려 TRACE 쪽이 빨랐는데, 두 실행 차이(생성 P95 29.5 → 20.3ms, 약 30%)는
같은 설정으로 연속 실행해도 나오는 편차 범위다(첫 측정 45.2ms 와 재측정 29.5ms 도 같은 설정이다). 실제 비용은 **로그 양** — 100초에 203MB(약 2MB/s)가 쌓였다.
운영에서 SQL 로그를 끄는 이유는 지연이 아니라 디스크와 로그 검색성이다. 터미널(콘솔)에 직접 찍을 때의 비용은 재지 않았다.
이 실험 전에 이 문서와 `load/README.md` 에 적었던 "기본 로그가 지연을 몇 배로 부풀린다" 는 측정 없는 추정이었고, 측정으로 반증됐다.

**실행 간 편차**: 같은 설정(로그 끔)을 두 번 재서 생성 P95 45.2ms 와 29.5ms 가 나왔다. 이 문서의 한 자리 ms 차이는 의미가 없고, 2배 이상 차이만 변화로 본다.

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
