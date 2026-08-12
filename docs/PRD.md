# WE-Meet PRD — 전남대 기반 택시 동승 매칭 서비스 「가치가」

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.0 |
| 작성일 | 2026-08-12 |
| 팀 | 가치가 (전남대학교 인공지능학부) |
| 인원 | 5명 |
| 개발 기간 | 2026.08 ~ 2026.12 (5개월) |

---

## 0. 이 문서 사용법

이 문서는 **AI 코딩 어시스턴트에게 그대로 입력하기 위한 명세서**입니다.

- 모든 기능 요구사항에는 `FR-XX` 식별자가 붙어 있습니다. 작업 지시 시 `FR-03 구현해줘`처럼 참조하세요.
- §5(알고리즘)와 §7(데이터 모델)은 구현의 근거이므로, 코드 생성 요청 시 반드시 함께 전달하세요.
- §12~13(역할·협업 규칙)은 사람이 지키는 규약입니다. AI에게 코드를 요청할 때는 §13.4 코딩 컨벤션만 함께 넘기면 충분합니다.

---

## 1. 제품 개요

### 1.1 한 줄 정의

전남대학교 재학생 인증을 거친 학생끼리, 이동 경로가 비슷한 사람을 실시간으로 묶어주고 **택시 요금을 구간 단위로 공정하게 나눠주는** 폐쇄형 동승 매칭 서비스.

### 1.2 해결하려는 문제

| 대상 | 문제 |
|---|---|
| 학생 | 등·하교 피크 시간대 승차난, 단독 탑승 시 높은 교통비 부담 |
| 택시 업계 | 공차율 증가에 따른 수익성 악화, 대형 플랫폼 수수료 종속 |

### 1.3 핵심 차별점

1. **택시 호출·배차를 하지 않는다.** 매칭과 요금 분담이라는 본질에만 집중 → 규제·제휴 리스크 회피, MVP 실현 가능성 확보.
2. **구간 분할 정산.** 단순 1/N이 아니라, 카카오 API가 반환하는 구간별 거리를 그 구간에 탑승 중인 인원으로 나눠 요금을 산정 → "먼저 내리는 사람이 손해 본다"는 기존 동승의 근본 불만 해소.
3. **폐쇄형 신뢰 네트워크.** 학교 웹메일 인증으로 가입을 제한하고, 동성 매칭 옵션을 제공.

### 1.4 서비스 대상 거점

전남대학교(정문 · 후문 · 예대 삼거리), 경신여고, 유스퀘어, 광주송정역 — 4대 허브.

---

## 2. 범위 (Scope)

### 2.1 In Scope

- 학교 웹메일 기반 회원가입 및 인증
- 출발 거점 선택 + 목적지 검색(카카오 로컬 API)
- 실시간 동승 매칭 (최대 4인)
- 카카오모빌리티 다중 경유지 길찾기 기반 경로·요금 산출
- 구간 분할 요금 정산 가이드 (실결제 없음)
- 매칭된 그룹 전용 휘발성 인앱 채팅
- 매칭 이력 및 절감액 통계

### 2.2 Out of Scope (명시적 제외)

| 제외 항목 | 사유 |
|---|---|
| 택시 호출·배차 | 여객자동차법상 운송 중개 이슈. 실제 호출은 사용자가 카카오T 등으로 직접 수행 |
| 앱 내 결제·정산 이체 | 전자금융업 등록 필요. 시스템은 **분담액 계산 가이드만** 제공 |
| 네이티브 앱(iOS/Android) | 반응형 웹앱(PWA)으로 대체 |
| 기사용 앱 | 범위 외 |

> ⚠️ **법적 고지 문구를 UI에 반드시 노출할 것:** "본 서비스는 동승자 매칭 및 요금 분담 계산만 제공하며, 택시 호출·운송·결제를 중개하지 않습니다."

---

## 3. 사용자 및 시나리오

### 3.1 페르소나

| 페르소나 | 상황 | 니즈 |
|---|---|---|
| A. 통학생 (2학년) | 8:30 후문 → 경신여고 방면. 매일 택시 6,000원 | 반값에 정시 등교 |
| B. 귀향생 (1학년) | 금요일 17:00 정문 → 광주송정역. 짐이 많음 | 버스 대신 택시, 비용 분담 |
| C. 야간 귀가생 | 23:00 유스퀘어 → 전남대 | 안전 + 비용 |

### 3.2 핵심 시나리오 (Happy Path)

```
1. 로그인 (학교 웹메일 인증 완료 계정)
2. 출발 거점 선택 [후문] (프리셋 버튼)
3. 목적지 검색 "광주송정역" → 좌표 확정
4. 조건 설정: 최대 대기 10분 / 동성만 ON / 최대 우회 20%
5. [매칭 시작] → 대기열 진입, 대기 화면 (남은 시간 카운트다운)
6. 30초 후 매칭 성사 알림 (2명 그룹)
   → 지도에 합승 경로 표시
   → "혼자 12,400원 → 함께 6,900원 (5,500원 절약)"
   → 탑승 순서 / 하차 순서 안내
7. 그룹 채팅방 자동 개설 → "후문 앞 편의점에서 만나요"
8. [탑승 완료] 버튼 → 채팅방 종료, 이력 저장
```

---

## 4. 기능 요구사항

### 4.1 인증 (Auth)

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-01 | 웹메일 회원가입 | `@jnu.ac.kr` 도메인만 허용. 인증 코드(6자리) 메일 발송, 유효시간 10분, 5회 실패 시 30분 잠금 | P0 |
| FR-02 | 로그인 / 토큰 | JWT (Access 30분, Refresh 14일). Refresh 토큰은 DB 저장 및 회전(rotation) | P0 |
| FR-03 | 프로필 등록 | 닉네임, 성별(변경 불가), 학과·학년(선택). 실명·연락처 미수집 | P0 |
| FR-04 | 신고 및 차단 | 매칭 이력 기준 신고 접수. 누적 3회 시 7일 이용 제한 | P2 |

### 4.2 매칭 요청 (Ride Request)

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-05 | 출발 거점 선택 | 사전 등록된 허브 프리셋 버튼. 좌표는 서버가 보유 | P0 |
| FR-06 | 목적지 검색 | 카카오 로컬 키워드 검색 API. 자동완성 + 지도 핀 확인 | P0 |
| FR-07 | 매칭 조건 설정 | 최대 대기시간(5/10/15/20분), 동성 매칭 여부, 최대 허용 우회율(10/20/30%) | P0 |
| FR-08 | 대기열 등록 / 취소 | 1인 1건 동시 요청 제한. 취소 시 즉시 대기열 제거 | P0 |
| FR-09 | 대기 상태 표시 | 남은 대기시간, 현재 후보 인원 수 실시간 표시 (WebSocket) | P1 |
| FR-10 | 대기시간 만료 처리 | 만료 시 자동 취소 + "매칭 실패" 안내 + 재시도 버튼 | P0 |

### 4.3 매칭 엔진 (Matching)

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-11 | 배치 매칭 실행 | 30초 주기 tick. §5.1 알고리즘 수행 | P0 |
| FR-12 | 그룹 확정 | 최대 4인(택시 정원). 확정 시 전원에게 푸시/WebSocket 알림 | P0 |
| FR-13 | 매칭 수락·거절 | 확정 후 60초 내 전원 수락해야 성사. 1명이라도 거절/타임아웃 시 그룹 해체 후 재대기열 투입 | P1 |
| FR-14 | 그룹 해체 | 전원 하차 완료 또는 성사 전 이탈 시 그룹 종료 | P0 |

### 4.4 경로 및 요금 (Route & Fare)

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-15 | 합승 경로 산출 | 카카오모빌리티 다중 경유지 길찾기 호출 (§6.2) | P0 |
| FR-16 | 구간 분할 정산 | §5.2 공식으로 1인당 분담액 산출. 100원 단위 올림 | P0 |
| FR-17 | 절감액 표시 | 단독 탑승 요금 대비 절감액·절감률 표시 | P0 |
| FR-18 | 우회 보상 할인 | 우회율이 임계치를 넘는 사용자에게 §5.3 할인 계수 적용 | P1 |
| FR-19 | 경로 시각화 | 카카오맵 JS SDK Polyline. 탑승/하차 지점 순서 마커 | P0 |

### 4.5 채팅 (Chat)

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-20 | 그룹 채팅방 | WebSocket(STOMP). 매칭 성사 시 자동 개설 | P0 |
| FR-21 | 휘발성 처리 | 탑승 완료 또는 개설 후 3시간 경과 시 메시지 전량 삭제 | P0 |
| FR-22 | 정형 메시지 | "도착했어요", "5분 늦어요", "출발합니다" 퀵 버튼 | P1 |
| FR-23 | 연락처 보호 | 닉네임만 노출. 전화번호·이메일 패턴 자동 마스킹 | P1 |

### 4.6 이력 및 통계

| ID | 기능 | 상세 | 우선순위 |
|---|---|---|---|
| FR-24 | 매칭 이력 | 날짜, 경로, 동승 인원, 분담액, 절감액 | P1 |
| FR-25 | 누적 절감 리포트 | 개인 누적 절감액 및 이용 횟수 대시보드 | P2 |
| FR-26 | 관리자 통계 | 거점별·시간대별 매칭 성사율, 평균 절감률 | P2 |

---

## 5. 핵심 알고리즘 명세

> 이 장은 프로젝트의 기술적 핵심입니다. AI에게 구현을 맡길 때 반드시 전체를 전달하세요.

### 5.1 매칭 알고리즘

**입력:** 대기열 내 요청 리스트 `R = [r₁, r₂, ..., rₙ]`
**출력:** 확정 그룹 리스트 `G = [g₁, g₂, ...]`

#### Step 1 — 하드 필터 (동일 출발 거점 단위로 버킷 분리)

두 요청 `rᵢ`, `rⱼ`가 같은 그룹이 될 수 있는 필요조건:

```
1. 출발 거점 동일           : hub_i == hub_j
2. 출발 시간창 겹침          : |depart_i - depart_j| ≤ 5분
3. 성별 옵션 호환            : (same_gender_i || same_gender_j) → gender_i == gender_j
4. 목적지 근접               : haversine(dest_i, dest_j) ≤ 3.0 km
5. 정원                     : |group| ≤ 4
```

#### Step 2 — 목적지 클러스터링

- 목적지 좌표를 위경도 → 미터 단위 평면 좌표로 투영 (EPSG:5179 또는 근사 변환)
- 거리 기반 계층적 클러스터링(단일 연결법), 임계 거리 3km
- 클러스터 내에서 크기 2~4의 후보 조합 생성 (조합 폭발 방지를 위해 버킷당 최대 20개 요청까지만 처리)

#### Step 3 — 후보 조합 평가

각 후보 조합에 대해 카카오모빌리티 API를 1회 호출하여 실제 경로를 얻고 다음을 계산:

```
detour_ratio_i = (d_shared_i - d_solo_i) / d_solo_i
    d_solo_i   : i가 혼자 탔을 때의 직행 거리
    d_shared_i : 합승 경로에서 i가 실제로 이동한 거리 (탑승~하차 구간 합)

saving_i = fare_solo_i - share_i    (share_i는 §5.2로 계산)
```

**수용 조건 (하나라도 위반 시 조합 폐기):**

```
∀i : detour_ratio_i ≤ max_detour_i   (사용자 설정값)
∀i : saving_i > 0                    (아무도 손해 보지 않음)
```

#### Step 4 — 점수화 및 선택

```
score(g) = w₁ · Σsaving_i  −  w₂ · avg(detour_ratio)·10000  −  w₃ · max(wait_time_i)

초기 가중치: w₁ = 1.0, w₂ = 1.0, w₃ = 50
```

점수 내림차순으로 그리디 선택하되, 이미 배정된 사용자는 제외. 미배정 요청은 다음 tick으로 이월하며 대기시간이 누적될수록 `w₃` 항에 의해 우선순위가 자연 상승한다.

> **알고리즘 근거:** T-Share(IEEE ICDE)의 삽입 기반 스케줄링과 ExMAS의 그래프 그룹화 프레임워크를 참고. 본 서비스는 출발지가 소수 거점으로 고정되므로 일반 DARP보다 탐색 공간이 작아 그리디 휴리스틱으로 충분하다.

### 5.2 구간 분할 정산 (Fair Fare Calculator) — 핵심 로직

카카오모빌리티 API는 경유지가 N개일 때 **N+1개의 `sections`**를 반환하며, 각 section마다 `distance`(m)를 제공한다. 이를 이용해 "각 구간의 요금을 그 구간에 실제로 타고 있던 인원으로 나누는" 방식으로 정산한다.

```
총 요금            : F        = routes[0].summary.fare.taxi
총 거리            : D        = routes[0].summary.distance
구간 k의 거리       : d_k      = sections[k].distance
구간 k의 탑승 인원   : p_k      (해당 구간 주행 시 차량에 타고 있는 사람 수)

구간 k의 요금       : F_k      = F × (d_k / D)
사용자 i의 분담액    : share_i  = Σ_{k ∈ onboard(i)} (F_k / p_k)
```

**검증 조건 (반드시 테스트 코드로 확인):**

```
Σ share_i == F   (100원 단위 반올림 전 기준, 오차 허용치 1원 이내)
```

**반올림 규칙:** 각 `share_i`를 100원 단위 올림 → 합계가 `F`를 초과하는 잔액은 **분담액이 가장 큰 사용자**에게서 차감. (개별 사용자가 손해 보지 않도록)

#### 계산 예시

3인 합승, 총 요금 15,000원 / 총 거리 10,000m

| 구간 | 거리 | 탑승 인원 | 구간 요금 | 인당 |
|---|---|---|---|---|
| S1: 출발 → A하차 | 3,000m | A,B,C (3명) | 4,500원 | 1,500원 |
| S2: A → B하차 | 3,000m | B,C (2명) | 4,500원 | 2,250원 |
| S3: B → C하차 | 4,000m | C (1명) | 6,000원 | 6,000원 |

```
A = 1,500원        (단독 시 약 5,600원 → 73% 절감)
B = 1,500 + 2,250 = 3,750원
C = 1,500 + 2,250 + 6,000 = 9,750원
합계 = 15,000원 ✓
```

> 단순 1/N이었다면 A는 5,000원을 냈을 것이다. 구간 분할 방식은 **먼저 내리는 사람이 손해 보지 않는다**는 점에서 공정성이 근본적으로 다르며, 이것이 본 서비스의 핵심 설득 포인트다.

### 5.3 우회 보상 할인 (다이내믹 프라이싱)

마지막 하차자는 우회를 감수하지만 구간 분할상 가장 많은 금액을 부담한다. 이를 보정:

```
if detour_ratio_i > 0.15:
    discount_i = min(0.10, (detour_ratio_i - 0.15) × 0.5) × share_i
    share_i -= discount_i

할인 총액은 detour_ratio가 0.15 이하인 사용자들에게 분담액에 비례하여 재분배.
전체 합계 F는 항상 보존된다.
```

---

## 6. 외부 API 연동 — 카카오

### 6.1 카카오맵 JavaScript SDK (프론트엔드)

| 용도 | 내용 |
|---|---|
| 지도 렌더링 | 거점·목적지 마커, 합승 경로 Polyline |
| 키 | JavaScript 앱 키 (도메인 등록 필수) |
| 주의 | JS 키는 클라이언트 노출이 전제이므로, 카카오 콘솔에서 **허용 도메인을 반드시 제한**할 것 |

### 6.2 카카오모빌리티 다중 경유지 길찾기 (백엔드 전용)

```http
POST https://apis-navi.kakaomobility.com/v1/waypoints/directions
Authorization: KakaoAK {REST_API_KEY}
Content-Type: application/json

{
  "origin":      { "x": 126.8977, "y": 35.1760, "name": "전남대 후문" },
  "destination": { "x": 126.7913, "y": 35.1420, "name": "광주송정역" },
  "waypoints": [
    { "name": "user_2_dropoff", "x": 126.8500, "y": 35.1600 }
  ],
  "priority": "RECOMMEND",
  "summary": false
}
```

**응답에서 반드시 사용할 필드:**

| 경로 | 의미 | 용도 |
|---|---|---|
| `routes[0].summary.fare.taxi` | 예상 택시 요금(원) | 총 요금 F |
| `routes[0].summary.distance` | 총 거리(m) | 총 거리 D |
| `routes[0].summary.duration` | 총 소요시간(초) | 예상 도착 시간 |
| `routes[0].sections[k].distance` | 구간 거리(m) | **구간 분할 정산의 d_k** |
| `routes[0].sections[k].roads[].vertexes` | 좌표 배열 | 지도 Polyline |
| `routes[0].result_code` | 0이면 성공 | 실패 처리 분기 |

**제약 및 대응:**

| 제약 | 대응 |
|---|---|
| 경유지 최대 30개 | 정원 4인이므로 문제없음 |
| REST 키는 서버 전용 | **절대 프론트엔드에 노출 금지.** 백엔드가 프록시 |
| 무료 호출량 한도 존재 | 동일 (출발지, 목적지 조합) 결과를 Redis에 10분 캐싱. 매칭 tick당 호출 수 상한 설정 |
| API 장애 | Haversine 직선거리 × 보정계수(1.3) + 광주시 택시 요금표로 fallback 추정. UI에 "추정치" 배지 표시 |

### 6.3 카카오 로컬 API

키워드 장소 검색(`/v2/local/search/keyword.json`)으로 목적지 자동완성 및 좌표 획득. 백엔드 프록시 경유.

---

## 7. 데이터 모델

```
users
  id BIGINT PK
  email VARCHAR(100) UNIQUE          -- @jnu.ac.kr
  nickname VARCHAR(20) UNIQUE
  gender ENUM('M','F')
  verified_at DATETIME
  report_count INT DEFAULT 0
  status ENUM('ACTIVE','SUSPENDED')
  created_at DATETIME

hubs                                  -- 거점 프리셋
  id, name, lat DOUBLE, lng DOUBLE, type ENUM('CAMPUS','STATION','TERMINAL','SCHOOL')

ride_requests
  id BIGINT PK
  user_id BIGINT FK
  hub_id BIGINT FK
  dest_name VARCHAR(100), dest_lat DOUBLE, dest_lng DOUBLE
  depart_at DATETIME                  -- 희망 출발 시각
  max_wait_min INT
  same_gender_only BOOLEAN
  max_detour_ratio DECIMAL(3,2)       -- 0.10 / 0.20 / 0.30
  solo_distance INT                   -- 단독 직행 거리(m), 캐시
  solo_fare INT                       -- 단독 예상 요금(원), 캐시
  status ENUM('WAITING','MATCHED','CONFIRMED','CANCELLED','EXPIRED','COMPLETED')
  version INT                         -- 낙관적 락
  created_at, expires_at DATETIME

match_groups
  id BIGINT PK
  hub_id BIGINT FK
  total_fare INT, total_distance INT, total_duration INT
  route_json JSON                     -- 카카오 응답 원본(sections 포함)
  status ENUM('PENDING','CONFIRMED','COMPLETED','DISSOLVED')
  created_at, confirmed_at, closed_at DATETIME

match_members
  id BIGINT PK
  group_id BIGINT FK, request_id BIGINT FK, user_id BIGINT FK
  boarding_order INT, dropoff_order INT
  shared_distance INT                 -- 합승 경로 내 실제 이동 거리
  detour_ratio DECIMAL(4,3)
  share_amount INT                    -- 최종 분담액(원)
  saving_amount INT                   -- 절감액(원)
  accepted BOOLEAN DEFAULT FALSE
  UNIQUE(group_id, user_id)

chat_messages
  id BIGINT PK
  group_id BIGINT FK, sender_id BIGINT FK
  content VARCHAR(500)
  type ENUM('TEXT','SYSTEM','QUICK')
  created_at DATETIME
  -- 그룹 종료 또는 3시간 경과 시 스케줄러가 물리 삭제

refresh_tokens
  id, user_id FK, token_hash, expires_at, revoked BOOLEAN

reports
  id, reporter_id FK, reported_id FK, group_id FK, reason, created_at
```

**인덱스:** `ride_requests(status, hub_id, depart_at)`, `match_members(user_id)`, `chat_messages(group_id, created_at)`

---

## 8. API 명세 (요약)

> 상세 스펙은 Swagger(springdoc-openapi)로 자동 생성하며, **`/docs/api-spec.yaml`을 단일 진실 공급원(SSOT)으로 삼는다.** 프론트/백엔드는 이 스펙 확정 후 병렬 개발한다.

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/auth/signup` | 웹메일 인증 코드 발송 | — |
| POST | `/api/auth/verify` | 코드 검증 및 가입 완료 | — |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | — |
| POST | `/api/auth/refresh` | 토큰 재발급 | Refresh |
| GET | `/api/hubs` | 거점 목록 | ✔ |
| GET | `/api/places/search?q=` | 목적지 검색 (카카오 프록시) | ✔ |
| POST | `/api/requests` | 매칭 요청 생성 | ✔ |
| DELETE | `/api/requests/{id}` | 요청 취소 | ✔ |
| GET | `/api/requests/me` | 내 진행 중 요청 조회 | ✔ |
| POST | `/api/groups/{id}/accept` | 매칭 수락 | ✔ |
| POST | `/api/groups/{id}/reject` | 매칭 거절 | ✔ |
| GET | `/api/groups/{id}` | 그룹 상세 (경로·분담액) | ✔ |
| POST | `/api/groups/{id}/complete` | 탑승 완료 처리 | ✔ |
| GET | `/api/history` | 매칭 이력 | ✔ |
| POST | `/api/reports` | 신고 | ✔ |
| WS | `/ws/queue` | 대기 상태 실시간 수신 | ✔ |
| WS | `/ws/chat/{groupId}` | 그룹 채팅 (STOMP) | ✔ |

**공통 응답 포맷:**

```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "MATCH_EXPIRED", "message": "..." } }
```

---

## 9. 기술 스택 및 아키텍처

### 9.1 스택 확정

> ⚠️ **기존 계획서의 스택 충돌 해소 안내**
> 원 계획서 4.4항은 Java/Spring Boot, 일정표와 역할 배분은 FastAPI로 서로 다르게 기재되어 있었습니다. FastAPI를 담당하던 서동연 팀원의 이탈에 따라 **Java 17 + Spring Boot 3.x 단일 스택으로 통일**합니다. 이는 원 계획서의 도구 표(4.4)와 결론(8.1) 기재와도 일치합니다.

| 레이어 | 기술 | 비고 |
|---|---|---|
| Backend | Java 17, Spring Boot 3.2 | Web, Security, Data JPA, Validation, WebSocket |
| DB | MySQL 8.0 | |
| Cache / Queue | Redis 7 | 매칭 대기열, 카카오 응답 캐시, 토큰 블랙리스트 |
| Frontend | React 18 + Vite + TypeScript | |
| 지도 | 카카오맵 JS SDK | |
| 상태관리 | TanStack Query + Zustand | |
| 스타일 | Tailwind CSS | |
| API 문서 | springdoc-openapi (Swagger UI) | |
| 개발 환경 | Docker Compose (MySQL + Redis) | 전원 동일 환경 보장 |
| CI | GitHub Actions | PR 시 build + test 자동 실행 |
| 형상관리 | GitHub (monorepo) | |

### 9.2 아키텍처

```
[React SPA] ──HTTPS──> [Spring Boot API]
     │                        │
     │  WebSocket(STOMP)      ├── MySQL      (영속 데이터)
     └────────────────────────┤── Redis      (대기열, 캐시)
                              │
                              └──> 카카오모빌리티 길찾기 API
                                   카카오 로컬 API
                                   (REST 키는 서버에만 존재)

[Scheduler] 30초 tick → MatchingService.executeTick()
```

**패키지 구조 (도메인형)**

```
backend/src/main/java/com/gachiga/
├── auth/          # 인증, JWT              → 임승현
├── user/          # 회원, 프로필           → 임승현
├── ride/          # 요청, 대기열           → 이승민
├── matching/      # 매칭 엔진 (핵심)       → 서준
├── fare/          # 구간 분할 정산         → 서준 / 이승민
├── route/         # 카카오 API 클라이언트  → 송준호
├── chat/          # WebSocket 채팅         → 이승민
├── stats/         # 이력, 통계             → 송준호
└── common/        # 예외, 응답, 설정
```

> **도메인별 패키지 분리는 협업을 위한 필수 결정입니다.** 각자 자기 패키지 안에서만 작업하므로 머지 충돌이 구조적으로 최소화됩니다.

---

## 10. 비기능 요구사항

| 항목 | 목표치 | 검증 방법 |
|---|---|---|
| 매칭 API 응답 | P95 < 500ms | k6 부하 테스트 |
| 매칭 tick 처리 | 대기 100건 기준 < 2초 | 단위 테스트 |
| 동시 접속 | 200명 (피크 8~9시 가정) | k6 시나리오 |
| 정산 정확도 | Σ share = 총 요금, 오차 0원 | 프로퍼티 기반 테스트 |
| 가용성 | 카카오 API 장애 시 fallback 동작 | 장애 주입 테스트 |
| 보안 | 비밀번호 BCrypt, 개인정보 최소 수집, HTTPS 강제 | 코드 리뷰 체크리스트 |
| 테스트 커버리지 | `matching`, `fare` 패키지 80% 이상 | JaCoCo |

---

## 11. 엣지 케이스 (구현 필수)

| # | 상황 | 처리 |
|---|---|---|
| E-01 | 매칭 성사 후 1명 취소 | 잔여 인원 2명 이상이면 경로·요금 **재계산 후 전원에게 변경 알림 및 재동의 요청**. 1명만 남으면 그룹 해체 후 재대기열 |
| E-02 | 동일 사용자가 여러 그룹에 배정 | `ride_requests.version` 낙관적 락. 실패한 배정은 폐기 |
| E-03 | 카카오 API 타임아웃/오류 | 3초 타임아웃, 1회 재시도, 실패 시 Haversine fallback + "추정치" 표시 |
| E-04 | 대기시간 만료 직전 매칭 | 만료 30초 전부터는 신규 그룹 배정 중단 |
| E-05 | 노쇼 (수락 후 미등장) | 채팅 내 "노쇼 신고" 버튼. 누적 시 이용 제한 |
| E-06 | 동성 옵션 충돌 | 그룹 내 1명이라도 동성 옵션 ON이면 전원 동성이어야 함 |
| E-07 | 목적지가 사실상 동일 (100m 이내) | 경유지 생성 없이 단일 목적지로 처리, 균등 분할 |
| E-08 | 우회율 계산 시 분모 0 | 출발지=목적지인 비정상 요청은 생성 단계에서 차단 (최소 500m) |
| E-09 | 인증 토큰 만료 중 매칭 진행 | Refresh 자동 갱신. 실패 시 요청 유지하되 재로그인 유도 |
| E-10 | 채팅 중 그룹 해체 | 시스템 메시지 발송 후 5분 뒤 방 삭제 |

---

## 12. 팀 구성 및 역할

> 서동연 팀원 이탈에 따라 5인 체제로 재편했습니다. 이탈자가 담당하던 서버 아키텍처는 이승민, 보안 인프라는 임승현, 시스템 설계 문서는 서준이 흡수했습니다.

각자 **1차 도메인**(전 기간 책임)과 **2차 도메인**(1차가 마무리된 뒤 인수)을 갖는다. 1차만 배정하면 8~9월에 일이 몰리는 사람과 10~11월에 몰리는 사람이 갈려 전체 일정이 무너지기 때문이다.

| 이름 | 역할 | 1차 도메인 (8~9월) | 2차 도메인 (10~12월) | 포트폴리오 어필 포인트 |
|---|---|---|---|---|
| **서준** | PM / 알고리즘 리드 | `matching`, `fare` 인터페이스 설계 및 오프라인 프로토타입<br>스프린트 운영 | `matching`, `fare` 본구현 및 튜닝<br>기술 백서 집필 | "휴리스틱 매칭 알고리즘 및 구간 분할 정산 모델 설계·구현" — 기술 난도 최상위 영역 |
| **이승민** | 백엔드 코어 리드 | `common`, `ride`<br>서버 구조·예외·응답 표준<br>대기열 및 스케줄러 | `ride` 고도화<br>전체 API 통합 및 성능 튜닝 | "Spring Boot 실시간 매칭 서버 아키텍처 설계, P95 500ms 달성" |
| **임승현** | 인증·보안 / 실시간 | `auth`, `user`<br>JWT·웹메일 인증<br>DB 스키마 및 마이그레이션 | `chat` **(신규 인수)**<br>WebSocket STOMP 채팅<br>WS 핸드셰이크 인증 | "JWT 인증 체계 및 WebSocket 실시간 채팅 구현 (인증 연계 포함)" |
| **송준호** | 공간 데이터 / 지도 | `route`, `stats`<br>카카오모빌리티 클라이언트·캐싱<br>4대 거점 노드 데이터 | **프론트엔드 지도 파트 인수**<br>경로 시각화 컴포넌트<br>절감 효과 통계 리포트 | "카카오 API 연동 및 캐싱 최적화 + 합승 경로 시각화 구현" (백엔드·프론트 양쪽 경험) |
| **오승원** | 프론트엔드 / UI·UX | `frontend` 기반 구조<br>디자인 시스템, 라우팅<br>인증·요청 화면 | 매칭·정산·채팅 화면<br>UX 개선 및 베타 테스트 총괄 | "React 기반 실시간 매칭 웹앱 설계 및 사용성 검증" |

### 12.1 배분 원칙

1. **1인 1도메인 오너십** — 각자 1차 도메인의 설계·구현·테스트·문서를 끝까지 책임진다. 포트폴리오에 "내가 무엇을 만들었는가"를 한 줄로 쓸 수 있어야 한다.
2. **2차 도메인은 인접 영역으로만 이동** — 임승현이 채팅을 맡는 것은 WebSocket 핸드셰이크에 JWT 검증이 필요하기 때문이고, 송준호가 지도 컴포넌트를 맡는 것은 그가 카카오 응답 구조를 가장 잘 알기 때문이다. 학습 비용이 낮은 방향으로만 이동시킨다.
3. **크로스 리뷰 필수** — 오너십이 있어도 머지는 반드시 타인 리뷰를 거친다.
4. **인수인계는 문서가 아니라 코드로** — 2차 도메인 인수 시 기존 담당자가 인터페이스와 테스트를 먼저 만들어 두고 넘긴다.

### 12.2 기능 오너십 매핑 (FR ↔ 담당자 ↔ 코드 위치)

> AI에게 "내 역할 구현해줘"라고 지시했을 때 작업 범위가 결정되는 근거표다. §4의 FR 번호와 1:1로 대응한다.

| 담당 | FR | 코드 위치 | 선행 조건 |
|---|---|---|---|
| **서준** | FR-11, 12, 13, 14, 16, 17, 18 | `backend/.../matching/`, `backend/.../fare/` | 이승민 `common` 완료, 송준호 `route` 인터페이스 확정 |
| **이승민** | FR-08, 09, 10 + 전 도메인 공통 기반 | `backend/.../ride/`, `backend/.../common/`, `config/` | 없음 (**최우선 착수**) |
| **임승현** | FR-01, 02, 03, 04 / FR-20, 21, 22, 23 | `backend/.../auth/`, `.../user/`, `.../chat/`, `db/migration/` | 이승민 `common` 완료 |
| **송준호** | FR-05, 06, 15 / FR-19, 24, 25, 26 | `backend/.../route/`, `.../stats/`, `frontend/src/features/map/` | 없음 (**최우선 착수**) |
| **오승원** | FR-05~10, 12, 13, 17의 화면 / 전체 UI | `frontend/src/` (map 제외) | `docs/api-spec.yaml` v1 확정 |

**착수 순서:** 이승민(`common`) + 송준호(`route`)가 먼저 기반을 깔아야 서준·임승현이 움직일 수 있다. 8월 첫 스프린트는 이 두 사람의 작업이 크리티컬 패스다.

---

## 13. 협업 규칙

### 13.1 저장소 구조

```
we-meet/
├── backend/            # Spring Boot
├── frontend/           # React
├── docs/
│   ├── api-spec.yaml   # OpenAPI 명세 (SSOT)
│   ├── erd.md
│   └── adr/            # 주요 기술 결정 기록
├── docker-compose.yml
├── .github/
│   ├── workflows/ci.yml
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── ISSUE_TEMPLATE/
└── README.md
```

### 13.2 브랜치 전략 (Git Flow 간소화)

```
main         ← 배포 가능 상태만. 직접 push 금지 (브랜치 보호 설정)
 └ develop   ← 개발 통합 브랜치. 모든 feature가 여기로 머지
    ├ feat/#12-matching-engine
    ├ feat/#15-jwt-auth
    ├ fix/#20-fare-rounding
    └ docs/#22-api-spec
```

**규칙**
- 브랜치명: `{type}/#{issue번호}-{영문-요약}`
- feature 브랜치 수명은 **최대 3일**. 길어지면 작업 단위를 쪼갠다.
- 매일 아침 `develop`을 자기 브랜치로 rebase하여 충돌을 조기 발견한다.

### 13.3 커밋 및 PR

**커밋 메시지 (Conventional Commits)**

```
feat: 구간 분할 정산 로직 구현 (#12)
fix: 100원 단위 반올림 시 총액 불일치 수정 (#20)
refactor: RouteClient 캐싱 레이어 분리 (#18)
test: FareCalculator 프로퍼티 테스트 추가 (#12)
docs: API 명세에 매칭 수락 엔드포인트 추가 (#22)
chore: Docker Compose에 Redis 추가
```

**PR 규칙**

| 항목 | 규칙 |
|---|---|
| 크기 | 변경 500줄 이하 권장. 초과 시 분할 |
| 리뷰어 | 최소 1명 Approve 필수, **셀프 머지 금지** |
| 리뷰 SLA | 지정 후 24시간 이내 1차 응답 |
| 머지 방식 | Squash and merge |
| CI | build + test 통과가 머지 조건 |

**PR 템플릿**

```markdown
## 관련 이슈
Closes #

## 변경 내용
-

## 테스트 방법
-

## 리뷰 포인트
- (특히 봐줬으면 하는 부분)

## 체크리스트
- [ ] 로컬 빌드/테스트 통과
- [ ] API 변경 시 docs/api-spec.yaml 갱신
- [ ] 신규 환경변수는 .env.example에 추가
```

### 13.4 코딩 컨벤션

**Java**
- Google Java Style (IntelliJ 설정 파일을 `/docs`에 공유)
- 패키지 구조는 §9.2를 따른다
- **문자열 비교는 반드시 `.equals()` 사용.** `==` 사용 금지 — 동성 매칭 필터, 목적지 문자열 비교 등에서 논리 오류 원천 차단 *(원 계획서 명시 제약)*
- **문자열 파싱은 `StringTokenizer` 사용** *(원 계획서 명시 제약)*
  > 참고: `StringTokenizer`는 Java 공식 문서상 레거시 클래스로, 신규 코드에는 `String.split()`이 권장됩니다. 이 규칙이 교과목 요구사항이 아니라면 담당 교수님·멘토와 한 번 확인해 보시길 권합니다. 요구사항이라면 그대로 준수하되, 백서에 "명시적 제약 조건" 으로 기록해 두면 좋습니다.
- 예외는 `common/exception`의 커스텀 예외 + `@RestControllerAdvice`로 일괄 처리
- 금액 계산은 `int`(원 단위) 또는 `BigDecimal`. `double` 금지

**TypeScript / React**
- ESLint + Prettier (pre-commit hook으로 강제)
- 컴포넌트는 함수형 + 명명 export
- API 타입은 OpenAPI 스펙에서 자동 생성 (`openapi-typescript`)

### 13.5 스프린트 운영

| 항목 | 내용 |
|---|---|
| 주기 | 2주 |
| 도구 | GitHub Projects (Backlog / Todo / In Progress / In Review / Done) |
| 스프린트 플래닝 | 격주 월요일 — 이슈 생성 및 담당자 배정 |
| 데일리 스크럼 | 주 2회(화·금) 15분, 온라인 — 어제 한 일 / 오늘 할 일 / 막힌 것 |
| 스프린트 리뷰 | 격주 금요일 — 데모 + 회고, `/docs/retrospective`에 기록 |

### 13.6 협업의 핵심 — API 계약 우선 개발

프론트엔드(오승원)와 백엔드(이승민·임승현)가 서로를 기다리지 않기 위해:

1. 스프린트 시작 시 **`docs/api-spec.yaml`에 엔드포인트 스펙부터 합의·확정**한다.
2. 프론트엔드는 MSW(Mock Service Worker)로 목 서버를 띄워 병렬 개발한다.
3. 스펙 변경은 반드시 PR로 진행하고 전원에게 공지한다. **구두 변경 금지.**

### 13.7 환경 설정

```bash
git clone <repo> && cd we-meet
cp .env.example .env       # 카카오 키 등 입력
docker compose up -d       # MySQL + Redis 기동
cd backend && ./gradlew bootRun
cd frontend && npm i && npm run dev
```

- **비밀 키는 절대 커밋하지 않는다.** `.env`는 `.gitignore`에 포함, 팀 공유는 별도 채널로.
- 카카오 REST 키는 백엔드 환경변수에만 존재한다.

### 13.8 AI 코딩 어시스턴트 기반 역할별 작업 체계

각 팀원이 저장소를 pull한 뒤 AI에게 **"내 역할의 다음 작업을 진행해줘"** 한 마디로 자기 몫을 이어갈 수 있도록, 저장소에 역할 정의 파일을 둔다.

#### 13.8.1 동작 방식

```
we-meet/
├── CLAUDE.md                  # AI가 세션 시작 시 자동으로 읽는 공통 규칙
└── docs/
    ├── roles/
    │   ├── 서준.md            # 개인 작업 지시서 (담당 FR, 순서, 완료 기준)
    │   ├── 이승민.md
    │   ├── 임승현.md
    │   ├── 송준호.md
    │   └── 오승원.md
    └── PRD.md                 # 이 문서
```

- `CLAUDE.md`는 **모든 팀원에게 공통 적용**되는 규칙(코딩 컨벤션, 금지사항, 참조 문서)을 담는다.
- `docs/roles/{이름}.md`는 **그 사람의 담당 FR·패키지·작업 순서·완료 기준**을 담는다.

#### 13.8.2 사용법

> ⚠️ **중요:** AI는 git 이력이나 로그인 정보로 "지금 이 사람이 누구인지"를 자동으로 알지 못한다. **첫 지시에 자기 이름을 반드시 밝혀야 한다.**

```
✅ 올바른 지시
"나는 이승민이야. docs/roles/이승민.md 읽고 내 역할의 다음 미완료 작업을 진행해줘."

❌ 동작하지 않는 지시
"내 역할 구현해줘."          → AI가 '내'가 누구인지 모름
"이승민 역할 구현해줘."      → 파일을 안 읽고 이름만 보고 추측할 수 있음
```

**선택 사항 — 이름 생략하기.** `CLAUDE.md`에 다음 규칙을 넣으면 git 설정으로 자동 식별이 가능하다. 다만 각자 `git config user.name`을 실제 이름으로 정확히 설정해 두어야 하고, 100% 보장되는 방식은 아니므로 **첫 지시에는 이름을 직접 밝히는 것을 권장**한다.

```markdown
## 작업자 식별
사용자가 이름을 밝히지 않았다면 `git config user.name`을 실행해
docs/roles/ 아래 해당 이름의 파일을 읽는다.
일치하는 파일이 없으면 추측하지 말고 사용자에게 이름을 물어본다.
```

#### 13.8.3 전형적인 작업 흐름

```bash
git checkout develop && git pull            # 1. 최신 코드 동기화
```
```
2. AI에게: "나는 임승현이야. docs/roles/임승현.md 읽고 다음 작업 진행해줘."
3. AI가 미완료 체크리스트 확인 → 브랜치 생성 → 구현 → 테스트 작성
4. 본인이 코드 검토 및 로컬 실행 확인      ← 생략 금지
5. 커밋·푸시 후 PR 생성, 리뷰어 지정
```

### 13.9 AI 병렬 작업 시 반드시 지킬 것

5명이 각자 AI로 코드를 만들면 **서로 다른 다섯 벌의 컨벤션이 생긴다.** 이를 막기 위한 규칙이다.

#### 13.9.1 공유 기반은 먼저 얼린다

8월 첫 스프린트에서 **전원이 모여** 다음을 확정하고, 이후에는 PR 없이 변경하지 않는다.

| 대상 | 내용 | 오너 |
|---|---|---|
| `common/response/ApiResponse.java` | 공통 응답 래퍼 | 이승민 |
| `common/exception/` | 예외 계층 및 에러 코드 enum | 이승민 |
| `config/` | Security, WebSocket, Jackson 설정 | 이승민 |
| `docs/api-spec.yaml` | API 계약 | 서준(중재) |
| `db/migration/` | 스키마 | 임승현 |
| `build.gradle` / `package.json` | 의존성 | 이승민 / 오승원 |

**이 파일들을 AI가 임의로 수정하는 것이 가장 흔한 충돌 원인이다.** `CLAUDE.md`에 "위 파일은 담당자 승인 없이 수정 금지"를 명시한다.

#### 13.9.2 남의 패키지가 필요할 때

AI에게 다른 사람 담당 코드를 만들게 하지 않는다. 대신:

```
1. 필요한 기능을 Java 인터페이스로 정의한다 (자기 패키지 안에)
2. 테스트용 Stub 구현을 만들어 개발을 진행한다
3. 실제 구현이 필요한 사람에게 이슈로 요청한다
```

예: 서준이 매칭 엔진을 만들 때 카카오 API가 아직 없다면

```java
// matching 패키지 안에 인터페이스만 정의
public interface RouteProvider {
    RouteResult findRoute(Coordinate origin, List<Coordinate> waypoints, Coordinate dest);
}
// 송준호가 route 패키지에서 KakaoRouteProvider로 구현
// 그 전까지 서준은 FixedRouteProvider(테스트용)로 개발 진행
```

이 방식 덕분에 **송준호의 API 연동이 끝나기 전에도 서준은 알고리즘을 완성할 수 있다.** 5인 팀에서 서로 기다리지 않게 만드는 가장 중요한 장치다.

#### 13.9.3 마이그레이션 파일 충돌 방지

여러 명이 동시에 스키마를 건드리면 파일명이 겹친다. 명명 규칙:

```
V{날짜}_{순번}__{설명}.sql
예) V20260901_01__create_users.sql
    V20260901_02__create_ride_requests.sql
```

스키마 변경은 **임승현에게 요청 후 진행**한다. 각자 만들지 않는다.

### 13.10 AI 사용 시 주의사항

| 항목 | 내용 |
|---|---|
| **코드 이해 없는 머지 금지** | 자기가 설명하지 못하는 코드는 PR에 올리지 않는다. 면접에서 "이 부분 왜 이렇게 했어요?"에 답하지 못하면 포트폴리오로서 가치가 없다 |
| **AI 생성 코드도 리뷰 대상** | "AI가 짰으니 맞겠지"는 통하지 않는다. 특히 §5 정산 로직은 반드시 손으로 검산한다 |
| **테스트는 직접 설계** | 무엇을 검증해야 하는지는 사람이 정한다. AI에게는 "이 케이스를 검증하는 테스트"라고 구체적으로 지시한다 |
| **환각 주의 영역** | 카카오 API 필드명, Spring Boot 3.x 설정 문법은 AI가 자주 틀린다. §6과 공식 문서로 대조 확인한다 |
| **비밀 키 입력 금지** | 실제 API 키를 AI에게 붙여넣지 않는다. `.env.example`에는 항상 placeholder만 둔다 |

---

## 14. 로드맵

> 원 계획서는 7월 시작 기준이었으나 현재(8월 중순) 기준으로 5개월 일정으로 재편성했습니다.

| 기간 | 마일스톤 | 주요 산출물 | 주담당 |
|---|---|---|---|
| **8월** | 기반 구축 | 저장소·CI 세팅, ERD 확정, API 명세 v1, Docker 환경, 4대 거점 데이터 구축 | 전원 |
| **9월** | 인증 + 요청 플로우 | 웹메일 인증·JWT(임승현), 매칭 요청 CRUD·대기열(이승민), 카카오 클라이언트·캐싱(송준호), 프론트 기반 구조(오승원), 알고리즘 오프라인 프로토타입(서준) | 전원 |
| **10월** | 매칭 엔진 + 정산 | §5 알고리즘 및 정산 본구현(서준), 스케줄러 연동(이승민), **채팅 착수**(임승현), **지도 컴포넌트 착수**(송준호), 매칭 화면(오승원) | 전원 |
| **11월** | 통합 + 실시간 | 엔드투엔드 통합·성능 튜닝(이승민), WebSocket 채팅 완성(임승현), 경로 시각화 완성(송준호), 정산·채팅 화면(오승원) | 전원 |
| **12월** | 베타 테스트 + 백서 | 교내 베타 테스트(30명 목표, 오승원 총괄), 부하 테스트(이승민), 절감 지표 산출(송준호), 기술 백서(서준) | 전원 |

**월별 데모 데이:** 매월 말 전원이 자기 파트를 5분씩 시연한다. 진척 관리와 발표 연습을 겸한다.

---

## 15. 성과 지표

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| 매칭 성사율 | ≥ 60% | 성사 그룹 수 / 전체 요청 수 |
| 평균 요금 절감률 | ≥ 40% | (단독 요금 − 분담액) / 단독 요금 |
| 평균 매칭 대기시간 | ≤ 5분 | 요청 생성 ~ 그룹 확정 |
| 정산 정확도 | 오차 0원 | 자동화 테스트 |
| API 응답 P95 | < 500ms | k6 부하 테스트 |
| 베타 만족도 | ≥ 4.0 / 5.0 | 설문 |
| 테스트 커버리지 | 핵심 패키지 80% | JaCoCo |

---

## 16. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 사용자 임계치 미달 (매칭 상대 부재) | 높음 | 베타 시 시간대·거점을 집중(등하교 피크 + 후문)해 밀도 확보. 예약 매칭(사전 등록) 기능 추가 검토 |
| 카카오 API 무료 한도 초과 | 중간 | 캐싱, tick당 호출 상한, 조합 사전 필터링으로 호출 수 최소화 |
| 인원 이탈에 따른 일정 지연 | 중간 | 도메인 오너십 + 크로스 리뷰로 버스 팩터 완화. 문서화 필수 |
| 웹메일 인증 API 제공 불가 | 중간 | 학교 인증 API가 어려우면 `@jnu.ac.kr` 메일 발송 검증 방식으로 대체(이미 기본안) |
| 법적 이슈(운송 중개 오인) | 낮음~중간 | 호출·결제 기능 완전 배제, UI 고지 문구 상시 노출 |
| 개인정보 이슈 | 중간 | 실명·연락처 미수집, 채팅 휘발성 처리, 최소 수집 원칙 문서화 |

---

## 부록 A. 참고 문헌

1. *T-Share: A Large-Scale Dynamic Taxi Ridesharing Service* (IEEE ICDE) — 삽입 기반 스케줄링
2. *ExMAS: Evaluating the shareability of taxi trips* (Transportation Research Part C) — 그래프 기반 그룹화
3. *Mechanism design-based matching and pricing for multi-passenger ride-sharing* (J. of Intelligent Transportation Systems, 2026) — 하이퍼그래프 모델링 및 VCG 기반 가격 결정
4. *Dynamic Rideshare Matching Algorithms for the Taxipooling Service* (IEEE) — 거점형 동승 휴리스틱

## 부록 B. 데이터 소스

- 국토교통부 TIMS(택시운행정보관리시스템) — 격자별 영업통계, 노드링크 통행량
- 광주광역시 빅데이터 통합플랫폼 — 교통·유동인구·대중교통 승하차 데이터

## 부록 C. 원 계획서 대비 변경 사항

| 항목 | 변경 |
|---|---|
| 팀 구성 | 6인 → 5인 (서동연 이탈, 담당 업무 재분배) |
| 백엔드 스택 | Java/FastAPI 혼재 → **Java 17 + Spring Boot 3.2 단일화** |
| 지도/경로 | "Location API" → **카카오맵 SDK + 카카오모빌리티 길찾기 API** 확정 |
| 정산 방식 | 거리 비례 1/N → **구간 분할 정산**으로 고도화 (§5.2) |
| 일정 | 7~12월 → 8~12월 재편성 |
| 제외 항목 | "토큰 사용량 절감", "AI API Gateway/LLMOps 시장 조사", "중복 질문 패턴" 등 본 서비스와 무관한 항목 삭제 |
