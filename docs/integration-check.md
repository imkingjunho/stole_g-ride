# 통합 점검 기록 — 2026-10-29 (이승민 T2-2·T3-1~3)

기준: main `7f70f86` + 이번 로컬 변경. 2026-10-29에 `245ddec`에서 팀원 커밋 3개를 fast-forward pull했고,
기존 승민 작업을 보존했다. 아래 검증은 해당 작업 트리에서 수행했으며 실제 베타 배포는 수행하지 않았다.
후속 커밋·push 이력은 저장소의 Git 로그를 기준으로 확인한다.

## 결과와 범위

| 확인 항목 | 결과 | 범위·제한 |
|---|---|---|
| 백엔드 `./gradlew build` | 396개 통과, 실패·오류·skip 0 | Java 17, ArchUnit 포함. common/config/ride 173개 포함 |
| 프론트 `npm run lint && npm run build` | 통과 | 최신 지도·인증 갱신·SPA 변경 포함 |
| 프론트 `verify-ui.mjs` | 12개 시나리오 통과, 페이지 오류 0 | 시스템 Chrome, MSW·카카오 SDK 대역 |
| 프론트 `verify-real-transport.mjs` | 통과 | MSW 없는 로컬 HTTP 대역. 동시 401 갱신 1회·만료 Access 로그아웃 재시도 |
| `npm run verify:deployment -- http://127.0.0.1:5193` | 통과 | 로컬 preview의 login/request/waiting HTML·JS 자산. 실제 Vercel 검증 아님 |
| `node --test load/k6/ride-requests.test.cjs` | 9개 통과 | 모든 VU 로그인 검증·현재 회차 ID만 정리·로컬 주소 제한 등 |
| 신규 스키마 및 기존 4테이블 업그레이드 | 일치 | 격리 MySQL 8.0.46, 6테이블·PK 6·UNIQUE 4·조회 인덱스 4 |
| prod `ddl-auto: validate` | 기동 성공 | 로컬 MySQL TLS REQUIRED·비밀번호 설정 Redis. 외부 서버 아님 |
| 실제 REST API smoke | local 17개 / prod 15개 통과 | local은 10/28, prod는 최신 빌드로 10/29 재확인 |
| JWT 200 VU 부하 | threshold 전체 통과 | [성능 기록 §5](performance.md). 카카오 fallback·매칭 tick/WS 부하 제외 |
| 운영 Compose JWT 전달 | 검증 통과 | JWT_SECRET 누락·빈 값은 거절, 백엔드에 환경변수 전달 |

이전 `RefreshServiceTest` 2개 실패는 팀원 커밋 `bbbc1d4`로 해소됐다. 현재 실패 항목으로 남기지 않는다.
`e73ac4c`의 그룹/데모 지도·인증 갱신 및 `7f70f86`의 SPA fallback도 위 최신 검증에 포함했다.

## 실제 API와 데이터 흐름

격리 MySQL·Redis·Java 서버에서 토큰 없는 요청 401, 잘못된 로그인 401, 로그인, 프로필 조회, 거점 6개,
500m 미만 요청 400, 생성 201, 중복 생성 409, 내 요청 조회, Refresh 회전·이전 토큰 재사용 401,
취소 200·내 요청 없음, 로그아웃·폐기한 Refresh 재사용 401을 확인했다. 생성 때 Redis 큐에 들어가고 취소 때 제거됐다.

local의 가입·인증 코드는 격리 Redis에서 읽어 검증했다. 외부 SMTP 발송 성공을 검증한 것은 아니다.
백엔드 카카오 키는 비워 추정 경로(`estimated=true`)를 사용했다. 10/28 브라우저에서 실제 API 로그인과 거점 6개 표시까지
확인했으며 JavaScript 키가 없는 지도 경계에서 멈췄다. 실제 지도·검색·매칭 성사·채팅까지 완주했다고 기록하지 않는다.
10/29 프론트 자동 회귀의 지도 SDK 대역 검사와 이 실제 API 검증은 서로 다른 검증이다.

부하 테스트는 생성·취소 각각 11,271건, 409 0건, 부하 오류 0%, 검사 성공률 100%였다. 종료 직후 활성 요청 0건·
Redis 큐 0개를 확인했다. 10/28 첫 실행의 수면·timeout 실패도 [실패 기록](performance.md)에 보존했다.

## 스키마·환경·계약 반영

- `docker-compose.prod.yml`에 JWT_SECRET 필수 전달을 추가하고 `.env.example`, README, 배포 가이드에 주입 방법을 명시했다.
- `docs/schema.sql`을 실제 DB에서 다시 추출해 누락된 `hubs`, `refresh_tokens`를 포함했다. 기존 4테이블용 변경분은
  `docs/schema-upgrade-20261028.sql`, 재추출·읽기 전용 인덱스 검사는 `deploy/scripts/`에 있다.
- `CreateRideRequest`의 기존 Bean Validation과 OpenAPI를 맞췄다: destName 최소 길이·공백 금지,
  위도 -90~90·경도 -180~180. 생성 TypeScript 타입 구조는 같아서 자동 생성 파일을 수정할 필요가 없었다.
- 임승현 요청에 따라 `contract.auth.AccessTokenVerifier.verify(String): Optional<Long>`과 PRD §14.1/§14.5를 제공했다.
  입력은 Bearer 접두부를 뺀 Access 토큰, 정상 서명·필수 exp·양수 subject를 만족할 때만 사용자 ID를 반환한다.
  구현체와 STOMP 연결은 임승현 작업이다.
- 오승원 요청에 따라 PRD §14.6에 `RouteMapProps.estimated?: boolean`(기본 false, true면 dash)을 반영했다.
  송준호의 RouteMap 구현 및 오승원의 GroupRouteMap/DemoPage 전달·회귀 검사가 남아 있다.
- 교통 분석 문서에는 공식 원천 후보·담당·집계 정의를 반영했다. 원천 수집·실측 결과·튜닝 효과는 아직 없다.

## API 명세 대조와 남은 경계

`docs/api-spec.yaml` 21개 연산 중 실제 local Swagger에 14개가 있다. local 전용
`POST /api/dev/simulate` 1개가 추가 노출되어 런타임 총 15개다. 아래 7개는 아직 실제 컨트롤러가 없다.
이번 pull에는 백엔드 런타임·엔티티 변경이 없어 10/28 대조 결과가 유지된다.

| 담당 | 미구현 연산 |
|---|---|
| 서준 | `GET /api/groups/{groupId}`, `POST /api/groups/{groupId}/accept`, `/reject`, `/complete` |
| 서준 | `GET /api/history`, `GET /api/stats/me`, `GET /api/admin/stats` |

요청·거점 응답 필드는 계약과 일치한다. 추가 읽기 검토에서 다음을 확인했다.

- `UpdateProfileRequest.nickname`은 구현에서 필수지만 명세의 PATCH 입력은 선택이다. department/grade를 생략해도
  현재 구현은 null로 전달하므로 부분 수정 시 기존 값을 지울 수 있다. 누락·명시 null 의미를 담당자와 맞춰야 한다.
- `UserProfileResponse.of`는 저장된 status/suspendedUntil을 반환한다. 로그인은 유효 기간을 반영한 상태를 사용하므로
  정지 기간이 지난 사용자의 로그인·프로필 상태가 다를 수 있다. DTO 변환을 맞추고 경계 테스트가 필요하다.
- `QueueStatusPushScheduler`는 아직 연결 사용자별 `statusOf`를 호출한다. 제공된 `statusOfAll`로 교체해야
  기존 배치 쿼리 개선이 실제 WS 경로에 적용된다. 이번 REST 성능 수치로 WS 성능을 판단할 수 없다.
- 실운영 STOMP JWT 인증, 채팅방 멤버십 검사, 실제 그룹·이력 API와 프론트 연결은 후속 통합 대상이다.

## 담당자에게 보낼 요청 초안

아래 문구는 전달용으로 작성했으며 외부 메시지를 전송하지 않았다.

```text
[계약 변경 요청] 임승현 / auth·realtime / 제공된 AccessTokenVerifier 계약을 구현하고 STOMP CONNECT Bearer 인증에 연결 / 운영 실시간 인증을 완성해야 함. 서명·필수 만료·양수 사용자 ID 검증과 실패 시 연결 거절을 포함
[계약 변경 요청] 임승현 / realtime/QueueStatusPushScheduler.java / statusOfAll로 연결 사용자 일괄 조회 후 push / 승민의 배치 조회 개선을 실제 WS 경로에 반영
[계약 변경 요청] 임승현 / user/dto/UpdateProfileRequest.java·UserService.java / PATCH 누락·null 의미와 nickname 필수 여부를 API 명세에 맞춰 조율 / 일부 필드 수정 때 기존 값 소실 및 400 불일치 방지
[계약 변경 요청] 임승현 / user/dto/UserProfileResponse.java / 정지 만료를 반영한 상태를 로그인 응답과 통일 / 같은 사용자의 화면별 상태 불일치 방지
[계약 변경 요청] 송준호 / frontend/src/features/map/RouteMap.tsx / PRD §14.6 estimated 선택 속성·기본 false·true면 dash 구현 / 추정 경로 구분 계약 반영 완료
[계약 변경 요청] 오승원 / frontend/src/features/group/GroupRouteMap.tsx·app/DemoPage.tsx / RouteMap 구현 반영 뒤 estimated 전달 및 실선·점선 검사 / 서버 추정치 플래그를 지도에 연결
[계약 변경 요청] 서준 / matching·fare·stats / 명세의 그룹 4개·이력/통계 3개 API와 실제 매칭 연결 / 현재 런타임 7개 API 부재로 전체 시나리오 통합 불가
[계약 변경 요청] 송준호·서준 / docs/traffic-analysis.md에 반영할 원천 자료·집계 계약 / 수집 파일·기준일·단위·결측·성사 요청 집계/최초 확정 시각 제공 / 추정 수치 없이 교통 분석과 실제 매칭 지표를 연결
```

## 실제 배포 전 필요한 것

사용자가 서버·도메인은 아직 없으며 로컬 검증·배포 준비까지 진행하도록 지정했다. 서버/도메인과 운영 키 확보 후
[배포 절차](deploy.md)에 따라 HTTPS·prod validate·카카오 허용 도메인·실제 메일·JWT/STOMP·전체 시나리오를 확인한다.
프론트 Vercel의 실제 운영 프로젝트·URL·재배포 반영은 오승원과 확인한다. 현재 로컬 preview 검사는 그 증거가 아니다.
T3-2의 베타 서버 확인, T3-3 실제 배포, 상시 T2-4의 미완료 체크는 유지한다.
