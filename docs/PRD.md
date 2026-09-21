# 가치가(WE-Meet) PRD v2.0 — 전남대 택시 동승 매칭 서비스

| 항목 | 내용 |
|---|---|
| 문서 버전 | **v2.0** (v1.0: 2026-08-12) |
| 작성일 | 2026-09-19 |
| 팀 | 가치가 (전남대학교 인공지능학부) · 5명 |
| 저장소 | `github.com/imkingjunho/stole_g-ride` |
| 개발 기간 | 2026.09.21 ~ 2026.12 중순 (약 12주) |
| v2 변경 요지 | ① 협업 방식 전환: `develop`/PR/리뷰 → **`main` 하나에 각자 직접 push + 폴더 소유제** ② 5인 파트를 **작업량 20%씩** 재분배 ③ 모듈 간 **계약(contract)·스텁**을 먼저 깔아 "각자 올려도 항상 작동" ④ 로드맵을 9/21 기준으로 재편 |

---

## 0. 이 문서 읽는 법

**팀원은 세 군데만 먼저 읽으세요.**

| 알고 싶은 것 | 볼 곳 |
|---|---|
| 우리가 뭘 만드는지 | §1~§3 |
| 내 파트가 뭔지, 어느 폴더가 내 것인지 | **§12** |
| 코드를 어떻게 올리는지 (git) | **§13** |
| 오늘 내가 할 일 | `docs/roles/내이름.md` |

**AI 코딩 어시스턴트에게는 이 문서 전체가 근거 자료입니다.** 특히 §5(알고리즘), §6(카카오 API), §7(데이터 모델), §14(모듈 간 계약)은 구현의 유일한 기준이므로 AI가 임의로 다른 설계를 만들지 않도록 함께 전달합니다. 모든 기능에는 `FR-XX` 식별자가 있고, 작업 지시 시 `FR-08 구현해줘`처럼 참조합니다.

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

전남대학교(정문 · 후문 · 예대 삼거리), 경신여고, 유스퀘어, 광주송정역 — 4대 허브(6개 지점).

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

### 2.3 우선순위 정의 (v2에서 명확화)

| 등급 | 의미 | 기한 |
|---|---|---|
| **P0** | 12월 시연에서 **이게 없으면 시연이 안 되는** 기능. 핵심 시나리오(§3.2) 한 바퀴 | Phase 1 (10/25) |
| **P1** | 시연 품질을 결정하는 기능. 있어야 "서비스"로 보임 | Phase 2 (11/22) |
| **P2** | 여유가 있을 때. 없어도 시연 가능 | Phase 3 (선택) |

**P0 시연 시나리오가 실제 서버로 한 바퀴 돌아가는 것(10/25, §15 M1)이 이 프로젝트의 1차 목표다.** P1·P2는 그 위에 쌓는다.

---

## 3. 사용자 및 시나리오

### 3.1 페르소나

| 페르소나 | 상황 | 니즈 |
|---|---|---|
| A. 통학생 (2학년) | 8:30 후문 → 경신여고 방면. 매일 택시 6,000원 | 반값에 정시 등교 |
| B. 귀향생 (1학년) | 금요일 17:00 정문 → 광주송정역. 짐이 많음 | 버스 대신 택시, 비용 분담 |
| C. 야간 귀가생 | 23:00 유스퀘어 → 전남대 | 안전 + 비용 |

### 3.2 핵심 시나리오 (Happy Path) — 이것이 P0 전체다

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

> P0에서는 6번의 "수락/거절 60초"(FR-13)를 생략하고 **그룹 확정 = 즉시 성사**로 처리한다. 수락 단계는 P1에서 추가한다.

---

## 4. 기능 요구사항

> **담당** 열은 §12 파트 분배를 기능 단위로 풀어 쓴 것이다. `백`은 백엔드 API·로직, `프`는 화면. 한 기능이 두 사람에게 걸치면 둘 사이의 약속은 `docs/api-spec.yaml`(§14.4)이다.

### 4.1 인증 (Auth)

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-01 | 웹메일 회원가입 | `@jnu.ac.kr` 도메인만 허용. 인증 코드(6자리) 메일 발송, 유효시간 10분, 5회 실패 시 30분 잠금 | P0 | 백 임승현 / 프 오승원 |
| FR-02 | 로그인 / 토큰 | JWT (Access 30분, Refresh 14일). Refresh 토큰은 DB 저장 및 회전(rotation) | P0 | 백 임승현 / 프 오승원 |
| FR-03 | 프로필 등록 | 닉네임, 성별(변경 불가), 학과·학년(선택). 실명·연락처 미수집 | P0 | 백 임승현 / 프 오승원 |
| FR-04 | 신고 및 차단 | 매칭 이력 기준 신고 접수. 누적 3회 시 7일 이용 제한 | P2 | 백 임승현 / 프 오승원 |

### 4.2 매칭 요청 (Ride Request)

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-05 | 출발 거점 선택 | 사전 등록된 허브 프리셋 버튼. 좌표는 서버가 보유 (`GET /api/hubs`) | P0 | 백 송준호 / 프 송준호 |
| FR-06 | 목적지 검색 | 카카오 로컬 키워드 검색 API. 자동완성 + 지도 핀 확인 | P0 | 백 송준호 / 프 송준호 |
| FR-07 | 매칭 조건 설정 | 최대 대기시간(5/10/15/20분), 동성 매칭 여부, 최대 허용 우회율(10/20/30%) | P0 | 백 이승민(요청 필드) / 프 송준호 |
| FR-08 | 대기열 등록 / 취소 | 1인 1건 동시 요청 제한. 취소 시 즉시 대기열 제거 | P0 | 백 이승민 / 프 송준호 |
| FR-09 | 대기 상태 표시 | 남은 대기시간, 현재 후보 인원 수 실시간 표시 (WebSocket) | P1 | 백 이승민(데이터)·임승현(push) / 프 송준호 |
| FR-10 | 대기시간 만료 처리 | 만료 시 자동 취소 + "매칭 실패" 안내 + 재시도 버튼 | P0 | 백 이승민 / 프 송준호 |

### 4.3 매칭 엔진 (Matching)

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-11 | 배치 매칭 실행 | 30초 주기 tick. §5.1 알고리즘 수행 | P0 | 백 서준 |
| FR-12 | 그룹 확정 | 최대 4인(택시 정원). 확정 시 전원에게 WebSocket 알림 | P0 | 백 서준(확정)·임승현(push) / 프 오승원 |
| FR-13 | 매칭 수락·거절 | 확정 후 60초 내 전원 수락해야 성사. 1명이라도 거절/타임아웃 시 그룹 해체 후 재대기열 투입 | P1 | 백 서준 / 프 오승원 |
| FR-14 | 그룹 해체 | 전원 하차 완료 또는 성사 전 이탈 시 그룹 종료 | P0 | 백 서준 |

### 4.4 경로 및 요금 (Route & Fare)

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-15 | 합승 경로 산출 | 카카오모빌리티 다중 경유지 길찾기 호출 (§6.2). 실패 시 추정치 fallback | P0 | 백 송준호 |
| FR-16 | 구간 분할 정산 | §5.2 공식으로 1인당 분담액 산출. 100원 단위 올림 | P0 | 백 서준 |
| FR-17 | 절감액 표시 | 단독 탑승 요금 대비 절감액·절감률 표시 | P0 | 백 서준 / 프 오승원 |
| FR-18 | 우회 보상 할인 | 우회율이 임계치를 넘는 사용자에게 §5.3 할인 계수 적용 | P1 | 백 서준 |
| FR-19 | 경로 시각화 | 카카오맵 JS SDK Polyline. 탑승/하차 지점 순서 마커 | P0 | 프 송준호 |

### 4.5 채팅 (Chat)

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-20 | 그룹 채팅방 | WebSocket(STOMP). 매칭 성사 시 자동 개설 | P0 | 백 임승현 / 프 오승원 |
| FR-21 | 휘발성 처리 | 탑승 완료 또는 개설 후 3시간 경과 시 메시지 전량 삭제 | P0 | 백 임승현 |
| FR-22 | 정형 메시지 | "도착했어요", "5분 늦어요", "출발합니다" 퀵 버튼 | P1 | 백 임승현 / 프 오승원 |
| FR-23 | 연락처 보호 | 닉네임만 노출. 전화번호·이메일 패턴 자동 마스킹 | P1 | 백 임승현 |

### 4.6 이력 및 통계

| ID | 기능 | 상세 | 우선순위 | 담당 |
|---|---|---|---|---|
| FR-24 | 매칭 이력 | 날짜, 경로, 동승 인원, 분담액, 절감액 | P1 | 백 서준 / 프 오승원 |
| FR-25 | 누적 절감 리포트 | 개인 누적 절감액 및 이용 횟수 대시보드 | P2 | 백 서준 / 프 오승원 |
| FR-26 | 관리자 통계 | 거점별·시간대별 매칭 성사율, 평균 절감률 | P2 | 백 서준 |

---

## 5. 핵심 알고리즘 명세

> 이 장은 프로젝트의 기술적 핵심입니다. AI에게 구현을 맡길 때 반드시 전체를 전달하세요. (v1과 동일)

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
| `routes[0].sections[k].roads[].vertexes` | 좌표 배열 `[x, y, x, y, ...]` | 지도 Polyline |
| `routes[0].result_code` | 0이면 성공 | 실패 처리 분기 |

**제약 및 대응:**

| 제약 | 대응 |
|---|---|
| 경유지 최대 30개 | 정원 4인이므로 문제없음 |
| REST 키는 서버 전용 | **절대 프론트엔드에 노출 금지.** 백엔드가 프록시 |
| 무료 호출량 한도 존재 | 동일 (출발지, 목적지 조합) 결과를 Redis에 10분 캐싱. 매칭 tick당 호출 수 상한 설정 |
| API 장애 | Haversine 직선거리 × 보정계수(1.3) + 광주시 택시 요금표로 fallback 추정. 응답에 `estimated=true`, UI에 "추정치" 배지 표시 |

### 6.3 카카오 로컬 API

키워드 장소 검색(`/v2/local/search/keyword.json`)으로 목적지 자동완성 및 좌표 획득. 백엔드 프록시 경유(`GET /api/places/search`). 광주 지역으로 위치 편향 적용.

---

## 7. 데이터 모델

### 7.1 테이블

> **소유 모듈** 열이 v2에서 추가됐다. 테이블은 소유 모듈만 엔티티로 매핑하고, 다른 모듈은 **ID 값만 저장**한다(§7.2).

```
users                                 -- 소유: user (임승현)
  id BIGINT PK
  email VARCHAR(100) UNIQUE          -- @jnu.ac.kr
  password_hash VARCHAR(100)         -- BCrypt
  nickname VARCHAR(20) UNIQUE
  gender ENUM('M','F')
  department VARCHAR(50), grade INT  -- 선택
  verified_at DATETIME
  report_count INT DEFAULT 0
  status ENUM('ACTIVE','SUSPENDED')
  suspended_until DATETIME
  created_at DATETIME

refresh_tokens                        -- 소유: auth (임승현)
  id, user_id BIGINT, token_hash, expires_at, revoked BOOLEAN

reports                               -- 소유: user (임승현)
  id, reporter_id BIGINT, reported_id BIGINT, group_id BIGINT, reason, created_at

hubs                                  -- 소유: route (송준호). 거점 프리셋
  id, name, lat DOUBLE, lng DOUBLE, type ENUM('CAMPUS','STATION','TERMINAL','SCHOOL')

ride_requests                         -- 소유: ride (이승민)
  id BIGINT PK
  user_id BIGINT                      -- users.id (ID만 저장)
  hub_id BIGINT                       -- hubs.id (ID만 저장)
  dest_name VARCHAR(100), dest_lat DOUBLE, dest_lng DOUBLE
  depart_at DATETIME                  -- 희망 출발 시각
  max_wait_min INT
  same_gender_only BOOLEAN
  max_detour_ratio DECIMAL(3,2)       -- 0.10 / 0.20 / 0.30
  solo_distance INT                   -- 단독 직행 거리(m), 캐시
  solo_fare INT                       -- 단독 예상 요금(원), 캐시
  estimated BOOLEAN                   -- solo_fare 가 카카오가 아닌 추정치인지 (E-03, UI 배지)
  status ENUM('WAITING','MATCHED','CONFIRMED','CANCELLED','EXPIRED','COMPLETED')
  active_user_id BIGINT UNIQUE        -- 진행 중이면 user_id, 끝나면 NULL. 1인 1건(FR-08)을 DB 가 보장
  version INT                         -- 낙관적 락 (@Version)
  created_at, expires_at DATETIME

match_groups                          -- 소유: matching (서준)
  id BIGINT PK
  hub_id BIGINT
  total_fare INT, total_distance INT, total_duration INT
  route_json JSON                     -- RouteResult 원본(sections 포함)
  estimated BOOLEAN                   -- fallback 추정치 여부
  status ENUM('PENDING','CONFIRMED','COMPLETED','DISSOLVED')
  created_at, confirmed_at, closed_at DATETIME

match_members                         -- 소유: matching (서준)
  id BIGINT PK
  group_id BIGINT, request_id BIGINT, user_id BIGINT
  boarding_order INT, dropoff_order INT
  shared_distance INT                 -- 합승 경로 내 실제 이동 거리
  detour_ratio DECIMAL(4,3)
  share_amount INT                    -- 최종 분담액(원)
  saving_amount INT                   -- 절감액(원)
  accepted BOOLEAN DEFAULT FALSE
  UNIQUE(group_id, user_id)

chat_messages                         -- 소유: realtime (임승현)
  id BIGINT PK
  group_id BIGINT, sender_id BIGINT
  content VARCHAR(500)
  type ENUM('TEXT','SYSTEM','QUICK')
  created_at DATETIME
  -- 그룹 종료 또는 3시간 경과 시 스케줄러가 물리 삭제
```

**인덱스:** `ride_requests(status, hub_id, depart_at)`, `ride_requests(user_id, status)`, `UNIQUE ride_requests(active_user_id)`, `match_members(user_id)`, `chat_messages(group_id, created_at)`, `refresh_tokens(user_id)`

**Redis 키:** `queue:{hubId}` (Sorted Set, score=희망 출발 시각), `route:{좌표해시}` (경로 캐시, TTL 10분), `verify:{email}` (인증 코드, TTL 10분), `verify:fail:{email}` (실패 횟수, TTL 30분)

### 7.2 모듈 간 참조 원칙 — ID만 저장한다

```java
// ❌ 금지: 다른 모듈의 엔티티를 JPA 관계로 참조
@ManyToOne private User user;          // matching 모듈이 user 모듈 엔티티를 import

// ✅ 허용: ID 값만 저장. 사용자 정보가 필요하면 contract의 UserPort로 조회
private Long userId;
```

이 원칙 덕분에 **서준이 `MatchMember`를 만들 때 임승현의 `User` 클래스가 아직 없어도 컴파일된다.** 5명이 각자 올려도 빌드가 깨지지 않는 첫 번째 장치다. DB 레벨 FK는 두지 않는다(엔티티 관계가 없으면 Hibernate가 만들지 않음).

### 7.3 스키마 관리 방식 (v2 변경)

| 환경 | 방식 |
|---|---|
| 로컬 · 테스트 | `spring.jpa.hibernate.ddl-auto: update` — **엔티티가 곧 스키마.** 각자 자기 모듈 엔티티만 만들면 테이블은 자동 생성된다 |
| 베타/운영 | Phase 3에서 로컬 스키마를 `docs/schema.sql`로 추출해 적용하고 `validate`로 전환 |

Flyway 같은 마이그레이션 도구는 **쓰지 않는다.** 5명이 마이그레이션 파일을 나눠 쓰면 버전 번호 충돌과 "누가 먼저 올렸나" 문제가 가장 흔한 사고 원인이 되기 때문이다.

**주의 — `update`가 못 하는 것:** 컬럼 삭제·이름 변경·타입 변경은 반영되지 않는다. 이런 변경을 한 사람은 팀 단톡방에 공지하고, 각자 `docker compose down -v && docker compose up -d --wait`로 DB를 초기화한다. 컬럼 **추가**는 자동 반영되므로 공지가 필요 없다.

**초기 데이터(거점 6개):** route 모듈의 `ApplicationRunner`가 `hubs`가 비어 있을 때만 insert 한다(송준호).

---

## 8. API 명세 (요약)

> 상세 스펙은 **`docs/api-spec.yaml`(OpenAPI 3)이 단일 진실 공급원(SSOT)**이다. Phase 0에서 이승민이 이 표를 기준으로 전체 엔드포인트의 요청·응답 스키마를 확정하고, 이후 프론트는 여기서 타입을 자동 생성(`npm run gen:api`)하며 백엔드는 이 스펙과 Swagger 결과를 대조한다. 변경 절차는 §14.8.

| Method | Endpoint | 설명 | 인증 | 우선순위 | 백엔드 담당 |
|---|---|---|---|---|---|
| POST | `/api/auth/signup` | 웹메일 인증 코드 발송 | — | P0 | 임승현 |
| POST | `/api/auth/verify` | 코드 검증 및 가입 완료(비밀번호·프로필 포함) | — | P0 | 임승현 |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | — | P0 | 임승현 |
| POST | `/api/auth/refresh` | 토큰 재발급 (회전) | Refresh | P0 | 임승현 |
| POST | `/api/auth/logout` | Refresh 토큰 폐기 | ✔ | P1 | 임승현 |
| GET | `/api/users/me` | 내 프로필 | ✔ | P0 | 임승현 |
| PATCH | `/api/users/me` | 프로필 수정 (성별 제외) | ✔ | P1 | 임승현 |
| POST | `/api/reports` | 신고 | ✔ | P2 | 임승현 |
| GET | `/api/hubs` | 거점 목록 | ✔ | P0 | 송준호 |
| GET | `/api/places/search?q=` | 목적지 검색 (카카오 프록시) | ✔ | P0 | 송준호 |
| POST | `/api/requests` | 매칭 요청 생성 | ✔ | P0 | 이승민 |
| DELETE | `/api/requests/{id}` | 요청 취소 | ✔ | P0 | 이승민 |
| GET | `/api/requests/me` | 내 진행 중 요청 조회 (대기 상태 포함) | ✔ | P0 | 이승민 |
| GET | `/api/groups/{id}` | 그룹 상세 (경로·순서·분담액·절감액) | ✔ | P0 | 서준 |
| POST | `/api/groups/{id}/accept` | 매칭 수락 | ✔ | P1 | 서준 |
| POST | `/api/groups/{id}/reject` | 매칭 거절 | ✔ | P1 | 서준 |
| POST | `/api/groups/{id}/complete` | 탑승 완료 처리 | ✔ | P0 | 서준 |
| GET | `/api/groups/{id}/messages` | 채팅 이전 메시지 조회 (재접속용) | ✔ | P0 | 임승현 |
| GET | `/api/history` | 내 매칭 이력 | ✔ | P1 | 서준 |
| GET | `/api/stats/me` | 누적 절감 리포트 | ✔ | P2 | 서준 |
| GET | `/api/admin/stats` | 관리자 통계 | ✔ | P2 | 서준 |
| WS | `/ws` | STOMP 엔드포인트 (대기 상태·매칭 알림·채팅 — §14.5) | ✔ | P0 | 임승현 |

**공통 응답 포맷** (`common/response/ApiResponse`, 이승민):

```json
{ "success": true,  "data": { },   "error": null }
{ "success": false, "data": null,  "error": { "code": "MATCH_EXPIRED", "message": "..." } }
```

**에러 코드**(`common/exception/ErrorCode`, 이승민이 관리. 추가가 필요하면 카톡으로 요청): `INVALID_INPUT`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `EMAIL_DOMAIN_NOT_ALLOWED`, `VERIFY_CODE_MISMATCH`, `VERIFY_LOCKED`, `INVALID_TOKEN`, `ALREADY_IN_QUEUE`, `REQUEST_TOO_SHORT`, `MATCH_EXPIRED`, `GROUP_NOT_MEMBER`, `ROUTE_API_FAILED`, `USER_SUSPENDED`, `INTERNAL_ERROR`

---

## 9. 기술 스택 및 아키텍처

### 9.1 스택 (변경 금지)

| 레이어 | 기술 | 비고 |
|---|---|---|
| Backend | Java 17, Spring Boot 3.2 | Web, Security, Data JPA, Validation, WebSocket, Mail |
| DB | MySQL 8.0 | `ddl-auto: update` (§7.3) |
| Cache / Queue | Redis 7 | 매칭 대기열, 카카오 응답 캐시, 인증 코드 |
| Frontend | React 18 + Vite + TypeScript | |
| 지도 | 카카오맵 JS SDK (`react-kakao-maps-sdk`) | |
| 상태관리 | TanStack Query + Zustand | |
| 스타일 | Tailwind CSS | |
| API 문서 | springdoc-openapi (Swagger UI) + `docs/api-spec.yaml` | |
| 목 서버 | MSW (Mock Service Worker) | 프론트가 백엔드 없이 개발 |
| 아키텍처 검사 | ArchUnit | 모듈 경계 위반 시 **빌드 실패** (§9.3) |
| 개발 환경 | Docker Compose (MySQL + Redis) | 전원 동일 환경 |
| CI | GitHub Actions | `main` push 시 백엔드·프론트 빌드. 깨지면 커밋한 사람에게 메일 |
| 형상관리 | GitHub monorepo, **브랜치는 `main` 하나** | §13 |

### 9.2 아키텍처

```
[React SPA] ──HTTPS──> [Spring Boot API]  (모듈러 모놀리스: 앱 1개, 모듈 8개, 모듈 1개 = 사람 1명)
     │                        │
     │  WebSocket(STOMP)      ├── MySQL      (영속 데이터)
     └────────────────────────┤── Redis      (대기열, 캐시)
                              │
                              └──> 카카오모빌리티 길찾기 API / 카카오 로컬 API
                                   (REST 키는 서버에만 존재)

[matching 모듈 스케줄러] 30초 tick → executeTick()
[ride 모듈 스케줄러]     30초     → 만료 처리
[realtime 모듈 스케줄러] 5초      → 대기 상태 push / 1분 → 채팅 물리 삭제
```

**패키지 구조 (v2) — 패키지 1개 = 소유자 1명**

```
backend/src/main/java/com/gachiga/
├── GachigaApplication.java
├── common/      이승민   ApiResponse, ErrorCode, BusinessException, 전역 예외 처리, 공용 유틸(Haversine)
├── config/      이승민   Jackson, Redis, Swagger, CORS, 스케줄링
├── contract/    이승민   ⚠️ 동결. 모듈 간 port 인터페이스·DTO·이벤트·@CurrentUser  (§14)
├── ride/        이승민   RideRequest, 요청 API, Redis 대기열, 만료 스케줄러, RideRequestPort·QueueStatusPort 구현
├── matching/    서준     매칭 엔진·tick 스케줄러, MatchGroup·MatchMember, 그룹 API, MatchHistoryPort 구현
├── fare/        서준     구간 분할 정산, 우회 보상 할인
├── stats/       서준     이력·통계 API
├── auth/        임승현   Security 설정, JWT, 웹메일 인증, @CurrentUser 리졸버
├── user/        임승현   User, 프로필, 신고, UserPort 구현
├── realtime/    임승현   STOMP 설정·핸드셰이크 인증, 대기 상태 push, 매칭 알림 push, 채팅(저장·삭제·마스킹)
└── route/       송준호   카카오 클라이언트, 캐시, fallback, 호출량 제어, 장소 검색, Hub, RouteProvider·HubPort 구현

backend/src/main/resources/
├── application.yml           이승민   (spring.config.import 로 아래 domain/*.yml 을 읽는다)
├── application-local.yml     이승민
├── application-prod.yml      이승민
└── domain/                   ← 설정도 사람별 파일. 서로 안 겹친다
    ├── ride.yml              이승민   gachiga.ride.*
    ├── matching.yml          서준     gachiga.matching.* (가중치 w1~w3), gachiga.fare.*
    ├── auth.yml              임승현   gachiga.auth.*, spring.mail.*, gachiga.chat.*
    └── route.yml             송준호   gachiga.route.* (카카오 URL·타임아웃·tick당 호출 상한·캐시 TTL)

backend/src/test/java/com/gachiga/{패키지}/   → 그 패키지 소유자

frontend/src/
├── app/            오승원   라우터, Provider, 레이아웃
├── shared/         오승원   ui 컴포넌트(디자인 시스템), api 클라이언트, stores, ws 클라이언트, mocks(MSW)
├── generated/      (자동 생성) api-spec.yaml → api.d.ts. 손으로 수정 금지
├── features/
│   ├── auth/       오승원   회원가입·인증·프로필·로그인 화면
│   ├── request/    송준호   매칭 요청 화면, 대기 화면
│   ├── map/        송준호   RouteMap, DestinationPicker, useKakaoMap
│   ├── group/      오승원   매칭 성사·정산 상세 화면
│   ├── chat/       오승원   채팅 화면
│   └── history/    오승원   이력·누적 절감 대시보드
└── (package.json, vite.config.ts, tailwind.config.js 등 루트 설정)   오승원
```

### 9.3 모듈 경계 3원칙 — "각자 올려도 빌드가 안 깨지는" 이유

| # | 원칙 | 어기면 |
|---|---|---|
| 1 | **다른 모듈의 클래스를 import 하지 않는다.** 허용되는 것은 `contract.*`와 `common.*`만 | 상대가 클래스명을 바꾸면 내 코드가 깨진다. **ArchUnit 테스트가 빌드 단계에서 잡아낸다** |
| 2 | **다른 모듈의 테이블은 ID로만 참조한다** (§7.2) | 상대 엔티티가 아직 없으면 컴파일이 안 된다 |
| 3 | **다른 모듈에 알릴 일은 이벤트로, 물어볼 일은 port 인터페이스로** (§14) | 직접 호출은 순환 의존과 순서 문제를 만든다 |

`common/ArchitectureTest.java`(이승민, Phase 0)가 위 1번을 자동 검사한다. 예컨대 `matching` 패키지가 `com.gachiga.ride.RideRequest`를 import 하면 `./gradlew build`가 실패하므로, 규칙을 잊어도 main에 올라가기 전에 걸린다.

### 9.4 항상 켜져 있는 안전망

| 장치 | 무엇을 막나 | 누가 만드나 |
|---|---|---|
| 올리기 전 `./gradlew build` / `npm run build` (§13.3) | 컴파일 오류·테스트 실패가 main에 올라가는 것 | 각자 (습관) |
| GitHub Actions CI | 빌드 안 하고 올린 커밋. 빨간 X + 메일로 알림 | 이승민 (Phase 0) |
| ArchUnit 모듈 경계 테스트 | 남의 패키지 import | 이승민 (Phase 0) |
| Phase 0 스텁 (§14.7) | 남이 안 끝나서 내 것이 안 도는 상황 | 이승민 (Phase 0) → 각자 교체 |
| MSW 목 서버 | 프론트가 백엔드를 기다리는 상황 | 이승민 (Phase 0) → 오승원 유지 |

---

## 10. 비기능 요구사항

| 항목 | 목표치 | 검증 방법 | 담당 |
|---|---|---|---|
| 매칭 API 응답 | P95 < 500ms | k6 부하 테스트 | 이승민 |
| 매칭 tick 처리 | 대기 100건 기준 < 2초 | 단위 테스트 + 실행 시간 로그 | 서준 |
| 동시 접속 | 200명 (피크 8~9시 가정) | k6 시나리오 | 이승민 |
| 정산 정확도 | Σ share = 총 요금, 오차 0원 | 프로퍼티 기반 테스트 | 서준 |
| 가용성 | 카카오 API 장애 시 fallback 동작 | Mock 장애 주입 테스트 | 송준호 |
| 보안 | 비밀번호 BCrypt, 개인정보 최소 수집, 토큰 회전 | 자가 점검 체크리스트 | 임승현 |
| 테스트 커버리지 | `matching`, `fare` 패키지 80% 이상 | JaCoCo | 서준 |
| 빌드 건강 | `main`의 CI가 24시간 이상 빨간 상태로 방치되지 않음 | GitHub Actions | 전원 |

---

## 11. 엣지 케이스 (구현 필수)

| # | 상황 | 처리 | 담당 |
|---|---|---|---|
| E-01 | 매칭 성사 후 1명 취소 | 잔여 인원 2명 이상이면 경로·요금 **재계산 후 전원에게 변경 알림 및 재동의 요청**(`GroupRecalculated` 이벤트). 1명만 남으면 그룹 해체 후 재대기열 | 서준 |
| E-02 | 동일 사용자가 여러 그룹에 배정 | `ride_requests.version` 낙관적 락. `RideRequestPort.tryMarkMatched()`가 false를 돌려주면 그 배정은 폐기 | 이승민·서준 |
| E-03 | 카카오 API 타임아웃/오류 | 3초 타임아웃, 1회 재시도, 실패 시 Haversine fallback + `estimated=true` → UI "추정치" 배지 | 송준호·오승원 |
| E-04 | 대기시간 만료 직전 매칭 | 만료 30초 전부터는 `findWaiting()` 결과에서 제외 (신규 배정 중단) | 이승민 |
| E-05 | 노쇼 (수락 후 미등장) | 채팅 내 "노쇼 신고" 버튼 → `POST /api/reports`. 누적 시 이용 제한 | 임승현·오승원 |
| E-06 | 동성 옵션 충돌 | 그룹 내 1명이라도 동성 옵션 ON이면 전원 동성이어야 함 (§5.1 필터 3) | 서준 |
| E-07 | 목적지가 사실상 동일 (100m 이내) | 경유지 생성 없이 단일 목적지로 처리, 균등 분할 | 서준 |
| E-08 | 우회율 계산 시 분모 0 | 출발지=목적지인 비정상 요청은 생성 단계에서 차단 (최소 500m, `REQUEST_TOO_SHORT`) | 이승민 |
| E-09 | 인증 토큰 만료 중 매칭 진행 | 프론트 인터셉터가 Refresh 자동 갱신. 실패 시 요청은 유지하되 재로그인 유도 | 오승원·임승현 |
| E-10 | 채팅 중 그룹 해체 | `GroupDissolved` 수신 → 시스템 메시지 발송 후 5분 뒤 방 삭제 | 임승현 |
| E-11 | 스텁이 아직 실구현으로 교체되지 않은 상태에서 시연 | 스텁은 "그럴싸한 고정값"을 돌려주므로 시연은 가능. 단, 화면에 `DEV` 배지를 띄워 구분 | 각자 |

---

## 12. 파트 분배 — 5명 × 20%

### 12.1 분배 원칙

1. **1인 1파트, 파트 = 폴더 묶음.** 폴더 소유자만 그 안의 파일을 만들고 고친다. 남의 폴더는 읽기만 한다.
2. **파트끼리는 §14 계약으로만 만난다.** 인터페이스·이벤트·`api-spec.yaml`·WebSocket 프로토콜이 전부다. 남의 클래스를 직접 import 하지 않는다.
3. **각 파트는 혼자서도 시연할 수 있다.** 다른 파트가 안 끝났어도 Phase 0 스텁·MSW 위에서 자기 파트가 돈다.
4. **비중은 20%씩.** 아래 표의 작업량은 기능 수·난도·외부 의존을 합친 추정치(±2%)다. 한 사람이 일찍 끝나면 남의 폴더를 대신 만들지 않고 **통합 테스트·문서·발표 준비**를 가져간다.

### 12.2 파트 총괄표

| 파트 | 담당 | 한 줄 정의 | 소유 폴더 | 담당 FR | 비중 |
|---|---|---|---|---|---|
| ① 플랫폼·요청 | **이승민** | 뼈대·계약·공통·매칭 요청/대기열·통합 | `backend/…/common/ config/ contract/ ride/`, `resources/application*.yml`, `resources/domain/ride.yml`, 저장소 루트 파일(`build.gradle`, `docker-compose.yml`, `.env.example`, `.gitignore`, `.github/`), `docs/PRD.md`, `docs/api-spec.yaml`, `README.md`, `CLAUDE.md` | FR-07(API), 08, 09(데이터), 10 + Phase 0 + 통합·성능 | ~20% |
| ② 매칭·정산·통계 | **서준** | 매칭 엔진, 구간 정산, 그룹 API, 이력·통계, PM | `backend/…/matching/ fare/ stats/`, `resources/domain/matching.yml` | FR-11, 12, 13, 14, 16, 17, 18, 24, 25, 26 | ~20% |
| ③ 계정·실시간 | **임승현** | 웹메일 인증·JWT·프로필·신고, WebSocket 전부(대기 push·매칭 알림·채팅) | `backend/…/auth/ user/ realtime/`, `resources/domain/auth.yml` | FR-01, 02, 03, 04, 09(push), 12(push), 20, 21, 22, 23 | ~20% |
| ④ 경로·지도·요청 화면 | **송준호** | 카카오 연동·거점·장소 검색, 지도 컴포넌트, 요청·대기 화면 | `backend/…/route/`, `resources/domain/route.yml`, `frontend/src/features/request/`, `frontend/src/features/map/` | FR-05, 06, 07(UI), 15, 19 + FR-08·09·10 화면 | ~20% |
| ⑤ 프론트 기반·핵심 화면 | **오승원** | 디자인 시스템·라우팅·API 클라이언트, 인증·매칭 성사·정산·채팅·이력 화면, 베타 UX | `frontend/` 전체 (단, `features/request/`·`features/map/` 제외) | FR-01~04 화면, 12·13·17 화면, 20·22·24·25 화면 | ~20% |

**작업량 추정 근거** (총 100 기준)

| 파트 | 구성 요소별 추정 | 합계 |
|---|---|---|
| ① 이승민 | Phase 0 뼈대·계약·스텁·CI (8) + common/config (2) + 요청 API·Redis 대기열·만료 스케줄러 (6) + 통합 점검·부하 테스트·데모 시뮬레이터·배포 (4) | 20 |
| ② 서준 | 매칭 엔진 4단계 + tick (9) + 정산·우회 할인 (4) + 그룹 API·수락·해체·재계산 (3) + 이력·통계 (3) + PM·베타 진행·백서 (2) | 21 |
| ③ 임승현 | 웹메일 인증·JWT 회전·Security·프로필·신고 (8) + STOMP 설정·핸드셰이크 인증·대기/매칭 push (4) + 채팅 저장·자동 개설·삭제·퀵·마스킹 (7) | 19 |
| ④ 송준호 | 카카오 클라이언트·fallback·캐시·호출량 (6) + 거점·장소 검색 (2) + 지도 컴포넌트 3종 (4) + 요청·대기 화면 (6) + 성과 지표 산출 (1) | 19 |
| ⑤ 오승원 | 디자인 시스템·공통 컴포넌트·API 클라이언트·인터셉터·MSW 유지 (6) + 인증 화면 (3) + 성사·정산 화면 (5) + 채팅 화면 (2) + 이력 화면 (2) + 에러·반응형·법적 고지 (2) + 베타 피드백 UX 반영 (1) | 21 |

### 12.3 파트별 상세

#### ① 이승민 — 플랫폼·요청

- **하는 일:** Phase 0에서 팀 전체가 올라탈 뼈대를 만든다(§15 Phase 0). 그 뒤 `ride` 모듈(요청 생성·취소·조회, Redis 대기열, 만료 스케줄러, `RideRequestPort`·`QueueStatusPort` 구현)을 만들고, Phase 2~3에서 통합 점검·N+1 제거·k6 부하 테스트·데모 시뮬레이터·베타 배포를 맡는다.
- **혼자서 시연 가능한 것:** Swagger에서 요청 생성 → Redis 대기열에 들어감 → 만료되면 EXPIRED 처리.
- **다른 파트와의 접점:** 전원에게 `common`·`contract`·스텁을 제공. 계약·공용 파일 변경 요청의 유일한 접수처.
- **특수성:** Phase 0가 늦으면 4명이 멈춘다. 9/21~27 한 주는 이것만 한다. 이 파트는 실행보다 **"남이 막히지 않게 하는 일"**이 절반이다.

#### ② 서준 — 매칭·정산·통계 (+ PM)

- **하는 일:** §5 알고리즘 전체(하드 필터 → 클러스터링 → 조합 평가 → 점수화), 30초 tick 스케줄러, `MatchGroup`·`MatchMember`, 그룹 상세·수락·거절·완료 API, 해체·재계산(E-01), 구간 분할 정산(§5.2)과 우회 할인(§5.3), 이력·통계 API. PM으로 주간 통합 점검 진행, 베타 테스트 설계·진행, 기술 백서.
- **혼자서 시연 가능한 것:** `FixedRouteProvider`(스텁)와 인메모리 요청 목록으로 정산 단위 테스트, 시뮬레이션 1,000건 매칭 결과.
- **다른 파트와의 접점:** `RideRequestPort`(이승민)·`RouteProvider`·`HubPort`(송준호)·`UserPort`(임승현)를 **사용**하고, `MatchHistoryPort`를 **제공**하며, `GroupProposed/Confirmed/Recalculated/Dissolved/Completed` 이벤트를 **발행**한다.
- **특수성:** 기술 난도 최상. 그래서 남을 기다리지 않도록 계약이 먼저 얼려진다. **§5를 임의로 단순화하지 않는다** — 특히 정산을 1/N으로 바꾸지 않는다.

#### ③ 임승현 — 계정·실시간

- **하는 일:** 웹메일 인증(Spring Mail + Redis TTL), 비밀번호 BCrypt, JWT 발급·Refresh 회전, `SecurityFilterChain`과 `@CurrentUser` 리졸버, 프로필, 신고·이용 제한. 그리고 **WebSocket 전부**: STOMP 설정, CONNECT 시 JWT 검증, 그룹 멤버만 채팅 구독 가능하도록 인가, 대기 상태 push(5초), 매칭 알림 push(이벤트 수신), 채팅 저장·조회·자동 개설·물리 삭제·퀵 메시지·연락처 마스킹.
- **혼자서 시연 가능한 것:** 회원가입→메일 코드→로그인→JWT로 `/api/users/me`; STOMP 클라이언트 2개로 채팅 왕복.
- **다른 파트와의 접점:** `UserPort` **제공**, `QueueStatusPort`(이승민)·`MatchHistoryPort`(서준) **사용**, 그룹 이벤트 **수신**.
- **특수성:** WebSocket을 한 사람이 다 가진 이유 — STOMP 설정 파일이 두 사람에게 걸치면 그 파일이 충돌 1순위가 된다. v1에서 `/ws/queue`(이승민)와 `/ws/chat`(임승현)이 나뉘어 있던 것을 v2에서 합쳤다. Security 설정도 `config/`가 아니라 `auth/`에 둔다 — 인증 필터를 등록하는 사람이 설정 파일도 갖는다.

#### ④ 송준호 — 경로·지도·요청 화면

- **하는 일 (백엔드):** `KakaoRouteProvider`(§6.2), 3초 타임아웃·1회 재시도·Haversine fallback, Redis 캐시(좌표 4자리 반올림 키, TTL 10분), tick당 호출 상한·일일 카운터, 카카오 로컬 장소 검색 프록시, 거점(Hub) 엔티티·시드·`GET /api/hubs`, `RouteProvider`·`HubPort` 구현.
- **하는 일 (프론트):** `useKakaoMap` 훅, `RouteMap`(Polyline + 순서 마커 + bound 맞춤), `DestinationPicker`(검색 결과 핀·클릭 미세 조정), **매칭 요청 화면**(거점 프리셋·목적지 검색·조건 설정·법적 고지)과 **대기 화면**(WS 대기 상태 수신·카운트다운·만료 시 재시도).
- **혼자서 시연 가능한 것:** 요청 화면에서 거점 선택→목적지 검색→지도 핀→요청 생성(MSW 또는 실서버)→대기 화면; Swagger에서 실제 카카오 경로·요금 응답.
- **다른 파트와의 접점:** `RouteProvider`·`HubPort` **제공**(스텁 `EstimatedRouteProvider`는 그대로 fallback으로 남긴다), `RouteMap`을 오승원의 성사 화면이 **사용**(props 계약 §14.6).
- **특수성:** 백엔드·프론트 양쪽을 하는 유일한 파트. 요청 화면을 맡는 이유는 그 화면의 핵심(지도·장소 검색·거점)이 전부 이 파트의 데이터이기 때문이다.

#### ⑤ 오승원 — 프론트 기반·핵심 화면

- **하는 일:** 디자인 토큰·공통 컴포넌트(Button, Input, Card, Modal, Toast, Spinner, Badge), 레이아웃·라우팅·인증 라우트 가드, `openapi-fetch` API 클라이언트와 토큰 자동 갱신 인터셉터(E-09), STOMP 클라이언트 래퍼, MSW 핸들러 유지, 인증 3화면(가입·코드·프로필)+로그인, **매칭 성사 화면**(절감액 강조, 탑승·하차 순서, `RouteMap` 배치, P1에서 60초 수락/거절), **정산 상세 화면**(구간별 누가 어디까지 타고 얼마 내는지 시각화), 채팅 화면(퀵 버튼·마스킹 표시·휘발성 안내), 이력·누적 절감 대시보드, 에러·빈 상태·추정치 배지·`DEV` 배지, 반응형(360px~)·접근성, 베타 피드백 UX 반영.
- **혼자서 시연 가능한 것:** MSW만으로 §3.2 시나리오 전체를 클릭으로 한 바퀴.
- **다른 파트와의 접점:** `api-spec.yaml`(이승민)에서 타입 생성, `RouteMap`(송준호) 사용, WS 프로토콜(임승현) 구독.
- **특수성:** 심사자가 보는 건 결국 화면이다. **매칭 성사 화면과 정산 상세 화면이 프로젝트의 하이라이트**다.

---

## 13. 협업 방식 — `main` 하나로 일하는 법

### 13.1 왜 바꿨나

v1은 `develop` 브랜치 + 기능 브랜치 + PR + 리뷰 + Squash merge였다. 정석이지만, GitHub에 익숙하지 않은 팀에서는 **브랜치를 잘못 타고, PR이 쌓이고, 충돌이 나면 멈추는** 병목이 됐다. v2는 접근을 바꾼다. **충돌이 나지 않게 구조를 짜고, 그 위에서 가장 단순한 git만 쓴다.** 리뷰가 하던 "깨진 코드 차단" 역할은 빌드·CI·ArchUnit이 대신한다.

### 13.2 각자 올려도 작동하는 다섯 가지 장치

| # | 장치 | 무엇을 해결하나 |
|---|---|---|
| 1 | **폴더 = 사람** (§12.2) | git 충돌은 같은 파일을 두 사람이 고칠 때만 난다. 파일이 겹치지 않으면 충돌은 구조적으로 없다 |
| 2 | **계약 동결** (§14) | 상대 코드가 아니라 인터페이스에 의존하므로, 상대가 안 끝나도·다시 짜도 내 코드는 그대로다 |
| 3 | **스텁 선탑재** (§14.7) | 모든 인터페이스에 "그럴싸한 가짜"가 Phase 0부터 들어 있어 앱이 항상 뜬다. 각자 자기 스텁을 자기 진짜로 바꾼다 |
| 4 | **ID 참조·이벤트** (§7.2, §9.3) | 컴파일 의존이 없어 누가 먼저 올리든 빌드가 된다. ArchUnit이 위반을 잡는다 |
| 5 | **빌드 후 push + CI** (§13.4) | main에는 빌드 통과한 코드만 올라가고, 어기면 CI가 즉시 알린다 |

### 13.3 브랜치

```
main   ← 유일한 브랜치. 전원이 여기에 직접 push 한다.
```

- `develop`은 Phase 0에서 삭제한다. 기능 브랜치는 만들지 않는다. (혼자 실험용으로 로컬 브랜치를 쓰는 것은 자유지만, 올릴 때는 `main`으로 올린다.)
- 브랜치 보호 규칙은 걸지 않는다 — PR 없이 push 해야 하므로.

### 13.4 매일 루틴 (이것만 외우면 된다)

```bash
# ① 시작할 때 — 항상
git pull --rebase origin main

# ② 작업 — 내 폴더 안에서만 (AI에게: "나는 OOO이야. docs/roles/OOO.md 읽고 다음 작업 진행해줘.")

# ③ 올리기 전 — 빌드가 초록이어야만 다음으로
cd backend  && ./gradlew build        # 백엔드 담당자 (Windows: gradlew.bat build)
cd frontend && npm run lint && npm run build   # 프론트 담당자 (송준호는 둘 다)

# ④ 올리기
git add -A
git status                            # ← 내 폴더 파일만 떠야 한다. 남의 폴더가 보이면 git restore --staged 그파일
git commit -m "feat(ride): 매칭 요청 생성 API (FR-08)"
git pull --rebase origin main         # 그 사이 누가 올렸을 수 있다. 뭔가 받아졌으면 ③ 빌드를 한 번 더
git push origin main
```

**GitHub Desktop을 쓰는 경우:** `Fetch origin` → `Pull origin` → (터미널에서 빌드) → 변경 파일 목록이 내 폴더만인지 확인 → Summary에 커밋 메시지 → `Commit to main` → `Push origin`. 순서는 위와 같다.

**빈도:** 하루에 한 번 이상, **빌드가 통과하는 단위**로 올린다. 3일 넘게 안 올리면 위험 신호다(남들이 내 옛 스텁 위에서 일하고 있다). 반쯤 만든 기능도 컴파일만 되면 올려도 된다 — 화면에 연결하지 않았거나 `@Disabled`인 상태로.

### 13.5 문제가 생겼을 때

| 상황 | 처방 |
|---|---|
| `push` 거절 (`rejected`, `non-fast-forward`) | 누가 먼저 올린 것. `git pull --rebase origin main` → `git push origin main` |
| `pull --rebase` 중 **충돌(CONFLICT)** | 내 폴더만 만졌다면 원칙상 안 난다. 났다면 공용 파일(§13.7)을 누가 건드린 것. AI에게 "충돌 난 파일 정리해줘, 양쪽 변경 다 살려" → `git add 그파일` → `git rebase --continue`. 모르겠으면 `git rebase --abort` 하고 단톡방에 묻는다 |
| **main의 CI가 빨간 X** | 깨뜨린 사람(커밋에 이름이 있다)이 **30분 안에** 고쳐 올린다. 연락이 안 되면 누구든 `git revert <커밋해시>` → push 해서 복구한다. revert는 비난이 아니라 복구다 — 되돌려진 사람은 로컬에서 고쳐 다시 올리면 된다 |
| 실수로 남의 폴더를 고쳤다 (push 전) | `git restore 그파일` 또는 `git restore .` |
| 실수로 남의 폴더를 고쳐서 push 했다 | 단톡방에 알리고 폴더 주인이 판단한다. 주인이 `git revert`로 되돌려도 된다 |
| 계약(`contract/`, `api-spec.yaml`)을 바꿔야 한다 | 직접 고치지 말고 §13.7 절차 |
| 컬럼을 지우거나 이름을 바꿨다 | 단톡방 공지 → 각자 `docker compose down -v && docker compose up -d --wait` (§7.3) |

### 13.6 커밋 메시지

```
{type}({모듈}): {무엇을} (FR-XX)

feat(ride): 매칭 요청 생성 API (FR-08)
feat(fare): 구간 분할 정산 계산기 (FR-16)
fix(route): 캐시 키 좌표 반올림 누락 수정 (FR-15)
test(matching): 정산 합계 보존 프로퍼티 테스트 (FR-16)
feat(fe-group): 매칭 성사 화면 절감액 표시 (FR-17)
docs(roles): 임승현 T1-2 완료 체크
chore(contract): RouteResult에 estimated 필드 추가   ← 이승민만
```

`type`: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`. 모듈은 백엔드 패키지명 또는 `fe-{feature}`. 이슈 번호 대신 **FR 번호**를 적는다(이슈 트래커를 안 쓰기로 했으므로).

### 13.7 공용 파일과 계약 — 변경은 요청으로

아래 파일은 **이승민만 수정한다.** 5명이 각자 올리는 구조에서 유일하게 겹칠 수 있는 파일들이므로 한 사람이 관리한다.

| 파일 | 내용 |
|---|---|
| `backend/…/contract/**` | 모듈 간 인터페이스·DTO·이벤트 (§14) |
| `backend/…/common/**` | 공통 응답·에러 코드·예외 처리 |
| `docs/api-spec.yaml` | REST API 계약 |
| `backend/build.gradle`, `backend/src/main/resources/application*.yml` | 의존성·공통 설정 |
| `docker-compose.yml`, `.env.example`, `.gitignore`, `.github/**` | 인프라·CI |
| `docs/PRD.md`, `README.md`, `CLAUDE.md` | 팀 문서 |

**변경 요청 방법** — 단톡방에 한 줄:

```
[계약 변경 요청] 서준 / contract/route/RouteResult / sections에 duration(초) 추가 / 도착 예정 시각 계산에 필요
[에러코드 요청]  임승현 / ErrorCode / VERIFY_CODE_EXPIRED 추가 / 만료와 불일치를 구분해 안내하려고
[의존성 요청]    송준호 / build.gradle / spring-boot-starter-cache 추가 / 경로 캐시 @Cacheable 사용
[.env 항목 요청] 임승현 / .env.example / JWT_SECRET, MAIL_USERNAME, MAIL_PASSWORD 추가
```

이승민은 **24시간 안에** 반영해 push 하고 "반영했음, pull 하세요"라고 공지한다. 급하면 요청자가 자기 모듈 안에 임시 필드·상수로 우회하고 나중에 계약으로 올린다.

`frontend/package.json`은 **오승원**이 같은 방식으로 관리한다(송준호가 라이브러리를 추가해야 하면 요청). 단, Phase 0에서 지도·STOMP·MSW 등 알려진 의존성은 미리 넣어 두므로 요청은 드물 것이다.

### 13.8 팀 리듬

| 언제 | 무엇 | 진행 |
|---|---|---|
| **매주 1회, 30분, 온라인** (요일·시간은 팀에서 정함) | **통합 점검**: 전원이 그 자리에서 `git pull` → 실행 → 각자 자기 파트 3분 시연 → 막힌 것·요청할 것 | 서준(PM) |
| 매월 마지막 통합 점검 | **데모 데이**: 각자 5분 시연 + 다음 달 목표 | 서준 |
| 수시 | 단톡방. **막히면 3시간 안에 말한다.** 혼자 이틀 붙잡는 것이 팀에 가장 손해 | 전원 |

통합 점검의 핵심은 **"지금 main을 받으면 도는가"를 매주 전원이 눈으로 확인하는 것**이다. 이게 리뷰를 대체한다.

### 13.9 AI 코딩 어시스턴트 사용 규칙 (요약 — 상세는 `CLAUDE.md`)

1. 첫 지시에 **이름을 밝힌다**: `나는 송준호야. docs/roles/송준호.md 읽고 다음 미완료 작업 진행해줘.`
2. AI가 **내 폴더 밖 파일을 고치면 되돌린다.** 계약·공용 파일이 필요하면 AI는 §13.7 요청 문구를 대신 써 준다.
3. **커밋 전에 빌드.** AI는 사용자 확인 없이 커밋·push 하지 않는다.
4. **내가 설명 못 하는 코드는 올리지 않는다.** 이해가 안 되면 "이 코드 한 줄씩 초보도 알게 설명해줘".
5. 실제 API 키·비밀번호를 AI 채팅에 붙이지 않는다.
6. 카카오 API 필드명, Spring Boot 3.x 설정 문법은 AI가 자주 틀린다. §6과 공식 문서로 대조한다.

### 13.10 코딩 컨벤션

**Java**
- Google Java Style
- **문자열 비교는 반드시 `.equals()`. `==` 금지** (팀 명시 제약)
- **문자열 파싱은 `StringTokenizer` 사용** (팀 명시 제약 — 교과 요구사항이면 준수, 아니면 `String.split()` 권장. 백서에 "명시적 제약"으로 기록)
- 금액은 `int`(원) 또는 `BigDecimal`. **`double`/`float` 금지** (좌표·거리·비율은 `double` 허용)
- 예외는 `common/exception`의 `BusinessException` + `ErrorCode`. `RuntimeException` 직접 throw 금지
- Lombok 사용. 엔티티는 `@NoArgsConstructor(access = PROTECTED)`, setter 대신 의도가 드러나는 메서드
- Service는 인터페이스 없이 클래스 단일 구현. 인터페이스는 `contract/`의 port에만 둔다
- 주석·커밋 메시지는 한국어, 식별자는 영어

**TypeScript / React**
- ESLint + Prettier (`npm run lint`가 CI에서 돈다)
- 함수형 컴포넌트, 명명 export, `any` 금지
- API 타입은 `src/generated/api.d.ts`(자동 생성)만 사용. 손으로 중복 정의 금지
- 서버 상태는 TanStack Query, 클라이언트 상태는 Zustand
- 모바일 우선. 터치 영역 44px 이상

### 13.11 환경 설정

```bash
git clone https://github.com/imkingjunho/stole_g-ride.git && cd stole_g-ride
cp .env.example .env               # 카카오 키 등 입력 (Windows: copy .env.example .env)
docker compose up -d --wait        # MySQL + Redis
cd backend  && ./gradlew bootRun   # → http://localhost:8080/swagger-ui.html
cd frontend && npm install && npm run dev   # → http://localhost:5173  (기본은 MSW 목 모드)
```

- 비밀 키는 절대 커밋하지 않는다. `.env`는 `.gitignore`에 있고, 실제 키는 단톡방이 아닌 별도 채널로 받는다.
- 프론트 `.env`: `VITE_API_MODE=mock|real` 로 MSW와 실서버를 전환한다. `VITE_KAKAO_JS_KEY`는 JS 키(도메인 제한 필수).

---

## 14. 모듈 간 계약 (Contract) — 동결 목록

> `backend/src/main/java/com/gachiga/contract/`는 **Phase 0에서 이승민이 이 장을 그대로 코드로 옮겨 만들고, 이후 동결**한다. 모듈 사이에 오가는 것은 여기 있는 것이 전부다. 변경은 §14.8 절차로만.

### 14.1 port 인터페이스

```java
// ── contract/route ─────────────────────────────────────────────
public record Coordinate(double lat, double lng) {}

public record RouteResult(
        int totalFare,            // 원. summary.fare.taxi
        int totalDistance,        // m
        int totalDuration,        // 초
        List<Section> sections,   // 경유지 N개 → N+1개. 순서대로
        boolean estimated,        // true = 카카오 실패로 fallback 추정치
        String rawJson) {         // 카카오 응답 원본 (없으면 null)
    public record Section(int distance, int duration, List<Coordinate> path) {}
}

public interface RouteProvider {
    /** origin → waypoints(순서대로 하차) → destination. 실패해도 예외 대신 estimated=true 로 돌려준다. */
    RouteResult findRoute(Coordinate origin, List<Coordinate> waypoints, Coordinate destination);
}

public record HubInfo(Long id, String name, double lat, double lng, String type) {}

public interface HubPort {
    Optional<HubInfo> findById(Long hubId);
    List<HubInfo> findAll();
}

// ── contract/user ──────────────────────────────────────────────
public enum Gender { M, F }
public enum UserStatus { ACTIVE, SUSPENDED }
public record UserSummary(Long id, String nickname, Gender gender, UserStatus status) {}

public interface UserPort {
    Optional<UserSummary> findById(Long userId);
}

// ── contract/ride ──────────────────────────────────────────────
public record WaitingRequest(
        Long requestId, Long userId, Long hubId,
        Coordinate destination, String destName,
        LocalDateTime departAt, LocalDateTime expiresAt, LocalDateTime createdAt,
        boolean sameGenderOnly, BigDecimal maxDetourRatio,
        int soloDistance, int soloFare, int version) {}

public interface RideRequestPort {
    /** status=WAITING 이고 만료 30초 이상 남은 요청 전체 (E-04). matching 이 tick 마다 호출 */
    List<WaitingRequest> findWaiting();
    /** WAITING → MATCHED. 낙관적 락 실패(이미 다른 그룹에 배정) 시 false (E-02) */
    boolean tryMarkMatched(Long requestId, int version);
    /** 그룹 해체 → 재대기열 */
    void markWaiting(Long requestId);
    void markConfirmed(Long requestId);
    void markCompleted(Long requestId);
}

public record QueueStatus(Long requestId, String status, int remainingSeconds, int candidateCount) {}

public interface QueueStatusPort {
    /** 사용자의 진행 중 요청 상태. 없으면 empty. realtime 이 5초마다 호출 */
    Optional<QueueStatus> statusOf(Long userId);
}

// ── contract/matching ──────────────────────────────────────────
public interface MatchHistoryPort {
    boolean isMember(Long groupId, Long userId);        // 채팅 구독 인가, 신고 검증
    List<Long> memberUserIds(Long groupId);
}

// ── contract/auth ──────────────────────────────────────────────
/** 컨트롤러 파라미터에 붙이면 현재 사용자 id(Long)가 주입된다. 리졸버 구현은 auth/ 소유 */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
```

**누가 만들고 누가 쓰나**

| 인터페이스 | 구현(제공) | 사용 |
|---|---|---|
| `RouteProvider` | 송준호 `route/` | 서준(조합 평가), 이승민(단독 요금 캐시) |
| `HubPort` | 송준호 `route/` | 서준(출발 좌표), 이승민(요청 검증) |
| `UserPort` | 임승현 `user/` | 서준(성별 필터), 임승현 `realtime/`(닉네임) |
| `RideRequestPort` | 이승민 `ride/` | 서준(대기열 읽기·상태 변경) |
| `QueueStatusPort` | 이승민 `ride/` | 임승현 `realtime/`(대기 상태 push) |
| `MatchHistoryPort` | 서준 `matching/` | 임승현 `realtime/`(채팅 인가), `user/`(신고 검증) |
| `@CurrentUser` 리졸버 | 임승현 `auth/` | 컨트롤러 전부 |

### 14.2 이벤트 (`contract/event/`)

Spring `ApplicationEventPublisher`로 발행하고 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받는다. **수신 측이 실패해도 발행 측 트랜잭션은 깨지지 않는다**(수신 측은 try/catch + 로그).

```java
public record RideRequestCreated(Long requestId, Long userId, Long hubId) {}
public record RideRequestCancelled(Long requestId, Long userId) {}
public record RideRequestExpired(Long requestId, Long userId) {}

public record GroupProposed(Long groupId, List<Long> userIds) {}      // 그룹 생성, 수락 대기 (P1). P0에서는 Confirmed 와 연달아 발행
public record GroupConfirmed(Long groupId, List<Long> userIds) {}     // 전원 수락 → 채팅방 개설 트리거
public record GroupRecalculated(Long groupId, List<Long> userIds) {}  // E-01 재계산 → 재동의 요청
public record GroupDissolved(Long groupId, List<Long> userIds, String reason) {}
public record GroupCompleted(Long groupId, List<Long> userIds) {}
```

| 이벤트 | 발행 | 수신 |
|---|---|---|
| `RideRequest*` | 이승민 `ride/` | 임승현 `realtime/`(대기 화면 갱신) |
| `Group*` | 서준 `matching/` | 임승현 `realtime/`(매칭 알림 push, 채팅방 개설·시스템 메시지·삭제 예약), 이승민 `ride/`(요청 상태 정리) |

### 14.3 공통 응답·에러 (`common/`)

`ApiResponse<T> { success, data, error }`, `ApiError { code, message }`, `ErrorCode` enum(§8), `BusinessException(ErrorCode)`, `@RestControllerAdvice` 전역 처리. 모든 컨트롤러는 `ApiResponse<T>`를 반환한다.

### 14.4 REST API 계약 — `docs/api-spec.yaml`

§8 표의 모든 엔드포인트에 대해 요청·응답 스키마를 OpenAPI 3로 기술한다. Phase 0 산출물. 프론트는 `npm run gen:api`로 `src/generated/api.d.ts`를 만들고, 백엔드는 구현 후 Swagger(`/v3/api-docs`)와 대조한다. **스펙과 구현이 다르면 구현이 틀린 것이다.**

핵심 응답 형태(그룹 상세 — 성사·정산 화면의 데이터):

```json
{
  "groupId": 17, "status": "CONFIRMED", "estimated": false,
  "hub": { "id": 2, "name": "전남대 후문", "lat": 35.1760, "lng": 126.8977 },
  "totalFare": 15000, "totalDistance": 10000, "totalDuration": 1320,
  "route": { "sections": [ { "distance": 3000, "duration": 400, "path": [ {"lat": 35.17, "lng": 126.89}, ... ] } ] },
  "members": [
    { "userId": 1, "nickname": "후문호랑이", "isMe": true, "boardingOrder": 1, "dropoffOrder": 1,
      "destName": "경신여고", "shareAmount": 1500, "soloFare": 5600, "savingAmount": 4100, "detourRatio": 0.02, "accepted": true }
  ],
  "acceptDeadline": "2026-10-20T08:31:00"
}
```

### 14.5 WebSocket 프로토콜 (STOMP over WebSocket, `/ws`)

| 항목 | 값 |
|---|---|
| 엔드포인트 | `ws://host/ws` (SockJS 미사용) |
| 인증 | STOMP `CONNECT` 프레임 헤더 `Authorization: Bearer {accessToken}`. 실패 시 연결 거부 |
| 구독 `/user/queue/status` | 내 대기 상태. 5초마다 + 변경 즉시. 본문 = `QueueStatus` JSON |
| 구독 `/user/queue/match` | 매칭 알림. `{ "type": "PROPOSED" \| "CONFIRMED" \| "RECALCULATED" \| "DISSOLVED" \| "COMPLETED", "groupId": 17, "message": "…" }` |
| 구독 `/topic/chat/{groupId}` | 채팅 수신. 그룹 멤버만 구독 가능(서버가 거부). 본문 = `ChatMessage` |
| 발신 `/app/chat/{groupId}` | `{ "type": "TEXT" \| "QUICK", "content": "…" }` |
| `ChatMessage` | `{ "id", "groupId", "senderId", "senderNickname", "type": "TEXT"\|"QUICK"\|"SYSTEM", "content", "createdAt" }` |

프론트 개발 모드(`VITE_API_MODE=mock`)에서는 `shared/ws`의 가짜 클라이언트가 위 메시지를 타이머로 흘려 준다(오승원).

### 14.6 프론트 계약

**라우트** (Phase 0에서 전부 등록. 각 화면은 처음엔 제목만 있는 placeholder)

| 경로 | 화면 | 담당 |
|---|---|---|
| `/login`, `/signup`, `/signup/verify`, `/signup/profile` | 인증 | 오승원 |
| `/` → `/request` | 매칭 요청 | 송준호 |
| `/waiting` | 대기 | 송준호 |
| `/groups/:groupId` | 매칭 성사·정산 상세 | 오승원 |
| `/groups/:groupId/chat` | 채팅 | 오승원 |
| `/history`, `/me` | 이력·대시보드·프로필 | 오승원 |

**공유 모듈** (`src/shared/`, 오승원 소유. Phase 0에서 초기 버전 제공)
- `api/client.ts` — `openapi-fetch` 클라이언트, 토큰 헤더, 401 시 refresh 후 재시도
- `stores/authStore.ts` — `{ accessToken, user, setTokens(), logout() }`
- `ws/stompClient.ts` — `connect()`, `subscribe(dest, cb)`, `send(dest, body)`; mock 모드 자동 전환
- `mocks/handlers.ts` — MSW 핸들러(api-spec 전체의 happy path)

**지도 컴포넌트 props** (`features/map/`, 송준호 제공 → `features/group/`, 오승원 사용)

```ts
export interface LatLng { lat: number; lng: number }
export interface Stop { order: number; kind: 'PICKUP' | 'DROPOFF'; position: LatLng; label: string }
export interface RouteMapProps {
  sections: { path: LatLng[] }[];   // 그룹 상세 응답의 route.sections 그대로
  stops: Stop[];                    // 탑승 1개 + 하차 N개
  className?: string;
}
export function RouteMap(props: RouteMapProps): JSX.Element;

export interface DestinationPickerProps {
  value: (LatLng & { name: string }) | null;
  onChange: (v: LatLng & { name: string }) => void;
}
export function DestinationPicker(props: DestinationPickerProps): JSX.Element;
```

### 14.7 Phase 0 스텁 목록 — 누가 무엇으로 바꾸나

스텁은 **미래 소유자의 폴더 안에** 만든다. 그래야 소유자가 자기 폴더만 고쳐서 교체할 수 있다.

| 스텁 | 위치 | Phase 0 동작 | 교체 담당 · 시점 |
|---|---|---|---|
| `DevSecurityConfig` + `DevCurrentUserResolver` | `auth/` | 전부 `permitAll`. `@CurrentUser` = 요청 헤더 `X-Dev-User`(기본 1) | 임승현 · Phase 1 (JWT 필터·실제 리졸버로) |
| `StubUserAdapter implements UserPort` | `user/` | id 1~4 고정 사용자(닉네임·성별) | 임승현 · Phase 1 |
| `EstimatedRouteProvider implements RouteProvider` | `route/` | Haversine × 1.3 + 광주 택시 요금표, `estimated=true` | 송준호 · Phase 1 (`KakaoRouteProvider` 추가, **이 클래스는 fallback으로 유지**) |
| `StaticHubAdapter implements HubPort` | `route/` | 거점 6개 하드코딩 | 송준호 · Phase 1 (DB + 시드) |
| `InMemoryRideRequestAdapter implements RideRequestPort, QueueStatusPort` | `ride/` | 인메모리 리스트 | 이승민 · Phase 1 (JPA + Redis) |
| `StubMatchHistoryAdapter implements MatchHistoryPort` | `matching/` | 항상 `true` / 빈 리스트 | 서준 · Phase 1 |
| `WebSocketConfig` (기본 브로커, 인증 없음) | `realtime/` | 연결·구독만 됨, push 없음 | 임승현 · Phase 1 |
| `ArchitectureTest` | `common/` | 모듈 경계 검사 (스텁 아님, 상시) | 이승민 유지 |
| MSW 핸들러 | `frontend/src/shared/mocks/` | api-spec 전체 happy path | 오승원 · 상시 유지 |
| placeholder 페이지 | `frontend/src/features/*/` | 라우트마다 제목만 | 각 화면 담당 · Phase 1 |

**교체 규칙:** 스텁을 지우고 진짜를 넣는다. 둘을 같이 두려면 진짜에 `@Primary`를 붙인다. 스텁이 남아 있는 화면·API에는 `DEV` 배지/로그를 남겨 시연 때 헷갈리지 않게 한다(E-11).

### 14.8 계약 변경 절차

1. 필요한 사람이 단톡방에 `[계약 변경 요청]` 한 줄(§13.7).
2. 이승민이 영향 받는 사람(구현자·사용자)에게 확인 — 보통 2명, 5분.
3. 이승민이 `contract/`·`api-spec.yaml`을 고쳐 push → "반영했음, pull 하세요".
4. 영향 받는 사람은 pull 후 빌드가 깨지면 자기 폴더를 맞춘다(보통 한 줄).

**추가는 쉽고 변경·삭제는 어렵다.** 필드 추가는 기존 코드를 깨지 않으므로 바로 반영한다. 필드 이름 변경·삭제·시그니처 변경은 영향 받는 사람이 같은 날 맞출 수 있을 때만 한다.

---

## 15. 로드맵 (2026-09-21 ~ 12월 중순)

### 15.1 단계

| 단계 | 기간 | 목표 | 마일스톤 |
|---|---|---|---|
| **Phase 0 — 뼈대** | 9/21(월) ~ 9/27(일), 1주 | 이승민이 §9·§14를 코드로 옮긴다. 나머지 4명은 환경 세팅 + 첫 push 연습 | **M0**: `main`을 clone → `docker compose up` → `bootRun` → Swagger 뜸 / `npm run dev` → 모든 화면이 placeholder+MSW로 클릭 가능 / CI 초록 / ArchUnit 통과 |
| **Phase 1 — P0 (스텁 → 진짜)** | 9/28 ~ 10/25, 4주 | 각자 P0 항목 전부. 수락 단계 없이 **자동 성사** | **M1**: 실서버로 §3.2 시나리오 한 바퀴 — 두 계정이 같은 거점에서 요청 → 30초 내 매칭 → 실제 카카오 경로·요금 → 지도 + 분담액 → 채팅 → 완료 |
| **Phase 2 — P1 + 안정화** | 10/26 ~ 11/22, 4주 | 수락/거절, 대기 상태 push, 우회 할인, 퀵 메시지, 마스킹, 이력, 지도 완성도, 엣지 케이스(§11) | **M2**: 3~4인 매칭 시연, E-01·E-03·E-09 재현 시연, 모바일 뷰 깨짐 없음 |
| **Phase 3 — 베타·마무리** | 11/23 ~ 12/13, 3주 | 교내 베타 30명, 부하 테스트, P2(여유 시), 백서·발표 자료 | **M3**: 12/13 코드 프리즈. 절감률·성사율 실측치 확보 |
| **최종 발표** | 12월 3주 (12/14~18, 학교 일정에 맞춰 확정) | | |

### 15.2 Phase 0 상세 — 이승민 (팀 전체의 출발선)

| 순서 | 산출물 | 완료 기준 |
|---|---|---|
| 0-1 | `develop` 브랜치 삭제, `.github/workflows/ci.yml`, README 배지 | main push 시 백엔드·프론트 빌드가 돌고 결과가 보인다 |
| 0-2 | `common/` — `ApiResponse`, `ApiError`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `GeoUtils`(Haversine) | 검증 실패·비즈니스 예외·500이 모두 `ApiResponse` 형태로 나온다 |
| 0-3 | `contract/` — §14.1·§14.2 전부 | 컴파일. Javadoc에 계약 의미 기술 |
| 0-4 | 스텁 전부(§14.7) 각 폴더에 배치 | 스텁만으로 `bootRun` 성공, Swagger에 스텁 API 노출 |
| 0-5 | `config/` — Jackson, Redis, Swagger(JWT 헤더 입력 UI), CORS(5173), `spring.config.import` + `domain/*.yml` 4개(빈 파일) | 각자 자기 yml에 설정을 넣으면 읽힌다 |
| 0-6 | `application.yml` `ddl-auto: update`, test 프로파일 정리 | `./gradlew build`가 DB 없이 통과 |
| 0-7 | `common/ArchitectureTest` (ArchUnit) | `matching`이 `ride.RideRequest`를 import 하면 빌드 실패하는 것을 확인 |
| 0-8 | `docs/api-spec.yaml` v1 — §8 전체 엔드포인트 스키마 | `npm run gen:api` 성공 |
| 0-9 | `frontend/` — Vite+React+TS, Tailwind, Router, TanStack Query, Zustand, `openapi-fetch`, `openapi-typescript`, MSW, `@stomp/stompjs`, `react-kakao-maps-sdk`, ESLint+Prettier. §14.6 라우트 전부 + placeholder 페이지 + `shared/` 초기 버전 + MSW 핸들러 | `npm run dev`로 §3.2 시나리오를 목으로 끝까지 클릭 |
| 0-10 | 이 문서 기준으로 `README.md`·`CLAUDE.md`·`docs/roles/*.md` 정합성 확인, 단톡방에 "Phase 0 끝, 시작하세요" | 전원이 clone 후 M0 재현 |

**Phase 0 동안 나머지 4명:** 설치·clone·실행(README 1단계), `docs/roles/내이름.md`와 이 문서 §12·§13·§14 읽기, **첫 push 연습** — `docs/roles/내이름.md`의 "환경 세팅 완료" 체크박스를 `[x]`로 바꿔 §13.4 루틴으로 main에 올려 본다.

### 15.3 사람별 Phase 목표 (요약 — 상세 체크리스트는 `docs/roles/`)

| | Phase 1 (10/25) — P0 | Phase 2 (11/22) — P1 | Phase 3 (12/13) |
|---|---|---|---|
| **이승민** | `RideRequest` JPA + Redis 대기열, 요청 생성·취소·조회 API, 만료 스케줄러, `RideRequestPort`·`QueueStatusPort` 실구현 | 데모 시뮬레이터(가상 요청 투입, local 전용), N+1 제거, 통합 점검(api-spec ↔ Swagger 대조) | k6 부하 테스트(P95<500ms), `docs/schema.sql` 추출·`validate` 전환, 베타 배포, `docs/performance.md` |
| **서준** | 정산기(§5.2)+합계 보존 테스트, 하드 필터, 클러스터링, 조합 평가, 점수화, `executeTick`, 그룹 생성·자동 성사, 그룹 상세·완료 API, 이벤트 발행 | 수락/거절 60초(FR-13), 해체·재계산(E-01), 우회 할인(§5.3), 이력 API, 시뮬레이션 1,000건 | 파라미터 튜닝, 통계 API(P2), 기술 백서, 발표 자료 총괄 |
| **임승현** | `User` JPA, 웹메일 코드 발송·검증·잠금, BCrypt, JWT+Refresh 회전, `SecurityFilterChain`, `@CurrentUser` 실구현, `UserPort`, STOMP CONNECT 인증, 매칭 알림 push, 채팅 저장·브로드캐스트·자동 개설·조회 API, 3시간 물리 삭제 | 대기 상태 push(5초), 퀵 메시지, 연락처 마스킹, 로그아웃, 프로필 수정, E-10 | 신고·이용 제한(P2), 보안 자가 점검, WS 재접속 안정화 |
| **송준호** | 거점 좌표 실측·`Hub` JPA·시드·`GET /api/hubs`, `KakaoRouteProvider`+fallback+캐시, 장소 검색 프록시, `useKakaoMap`·`RouteMap`·`DestinationPicker`, 요청 화면, 대기 화면(폴링 또는 목) | 호출량 제어·일일 카운터·캐시 적중률 로그, 대기 화면 WS 연동, 지도 완성도(bound·마커·추정치 배지), 목적지 클릭 미세 조정 | 트래픽 분석(공개 데이터)→튜닝 근거, 베타 절감률·성사율 산출 |
| **오승원** | 디자인 토큰·공통 컴포넌트, API 클라이언트·인터셉터, 인증 3화면+로그인+라우트 가드, 매칭 성사 화면(절감액·순서·`RouteMap`), 채팅 화면, 법적 고지 | 60초 수락/거절 UI, 정산 상세 시각화, 이력·대시보드, 에러·빈 상태·추정치·`DEV` 배지, 반응형·접근성 | 베타 피드백 UX 반영(전후 기록), 발표 시연 흐름 |

### 15.4 시연 정의

**M1 시연 (10/25 주 통합 점검)**
1. 브라우저 2개, 계정 A·B로 로그인 (실 JWT)
2. A: 후문 → 광주송정역 요청 / B: 후문 → 송정역 인근 요청
3. 30초 내 tick → 그룹 생성 → WS 알림 → 두 화면이 `/groups/:id`로 이동
4. 실제 카카오 경로가 지도에 그려지고, 분담액·절감액이 표시된다 (`estimated=false`)
5. 채팅 메시지 왕복, 채팅방 자동 개설 시스템 메시지 확인
6. 탑승 완료 → 요청 COMPLETED, 3시간 뒤 채팅 삭제 예약 로그

**M2 시연 (11/22 주)** — 3~4인 매칭, 1명 거절 → 재대기열, 1명 취소 → 재계산·재동의(E-01), 카카오 키를 틀리게 넣어 fallback·추정치 배지(E-03), 토큰 만료 후 자동 갱신(E-09), 360px 모바일 뷰.

**최종 발표** — M2 시연 + 베타 실측 지표(절감률·성사율·만족도) + 각자 5분 파트 설명.

---

## 16. 성과 지표

| 지표 | 목표 | 측정 방법 | 담당 |
|---|---|---|---|
| 매칭 성사율 | ≥ 60% | 성사 그룹 수 / 전체 요청 수 (베타 실측) | 송준호 |
| 평균 요금 절감률 | ≥ 40% | (단독 요금 − 분담액) / 단독 요금 | 송준호 |
| 평균 매칭 대기시간 | ≤ 5분 | 요청 생성 ~ 그룹 확정 | 서준 |
| 정산 정확도 | 오차 0원 | 자동화 테스트 | 서준 |
| API 응답 P95 | < 500ms | k6 부하 테스트 | 이승민 |
| 베타 만족도 | ≥ 4.0 / 5.0 | 설문 (30명) | 서준·오승원 |
| 테스트 커버리지 | `matching`·`fare` 80% | JaCoCo | 서준 |
| **main 건강도** | CI 빨간 상태 24시간 이상 방치 0회 | GitHub Actions 이력 | 전원 |

---

## 17. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| **Phase 0 지연** (이승민 한 사람에 집중) | 높음 — 4명이 대기 | 9/21~27은 이것만. 0-1~0-7(백엔드)을 먼저 끝내 백엔드 3명을 먼저 출발시키고, 0-8~0-9(프론트)는 그 다음. 27일까지 안 끝나면 오승원이 0-9를 자기 폴더에서 직접 진행 |
| **main이 깨진 채 방치** | 높음 — 전원 작업 중단 | CI 메일 + 30분 규칙 + 누구나 revert (§13.5). 통합 점검에서 매주 확인 |
| **계약 변경 병목** (이승민 응답 지연) | 중간 | 24시간 SLA. 급하면 자기 모듈 안에서 임시 우회 후 계약으로 승격. 추가는 즉시 반영 |
| **스텁이 영원히 남는다** | 중간 — 시연이 가짜 | roles 체크리스트의 "스텁 교체" 항목, 화면 `DEV` 배지, M1에서 `estimated=false`·실 JWT를 명시적으로 확인 |
| 남의 폴더를 AI가 고친다 | 중간 | CLAUDE.md 규칙 + `git status` 확인 습관 + ArchUnit(백엔드 import 위반) |
| 사용자 임계치 미달 (매칭 상대 부재) | 높음 | 베타 시 시간대·거점 집중(등하교 피크 + 후문). 데모 시뮬레이터로 시연 보장 |
| 카카오 API 무료 한도 초과 | 중간 | 캐싱, tick당 호출 상한, 조합 사전 필터링. 초과 시 fallback으로 시연 지속 |
| 인원 이탈 | 중간 | 파트가 폴더로 분리되어 있어 인수인계 범위가 명확. roles 파일 = 인수인계 문서 |
| 웹메일 발송 불가(SMTP 차단 등) | 중간 | Gmail 앱 비밀번호 등 대체 SMTP. 최악의 경우 local 프로파일에서 코드를 로그로 출력 |
| 법적 이슈(운송 중개 오인) | 낮음~중간 | 호출·결제 기능 완전 배제, UI 고지 문구 상시 노출 |
| 개인정보 이슈 | 중간 | 실명·연락처 미수집, 채팅 휘발성, 최소 수집 원칙 |

---

## 부록 A. 참고 문헌

1. *T-Share: A Large-Scale Dynamic Taxi Ridesharing Service* (IEEE ICDE) — 삽입 기반 스케줄링
2. *ExMAS: Evaluating the shareability of taxi trips* (Transportation Research Part C) — 그래프 기반 그룹화
3. *Mechanism design-based matching and pricing for multi-passenger ride-sharing* (J. of Intelligent Transportation Systems, 2026) — 하이퍼그래프 모델링 및 VCG 기반 가격 결정
4. *Dynamic Rideshare Matching Algorithms for the Taxipooling Service* (IEEE) — 거점형 동승 휴리스틱

## 부록 B. 데이터 소스

- 국토교통부 TIMS(택시운행정보관리시스템) — 격자별 영업통계, 노드링크 통행량
- 광주광역시 빅데이터 통합플랫폼 — 교통·유동인구·대중교통 승하차 데이터

## 부록 C. v1.0 → v2.0 변경 사항

| 항목 | v1.0 | v2.0 |
|---|---|---|
| 브랜치 | `main` + `develop` + 기능 브랜치 | **`main` 하나** |
| 코드 반영 | PR + 리뷰 1명 Approve + Squash merge | **빌드 통과 후 직접 push**. 리뷰 대신 CI·ArchUnit·주간 통합 점검 |
| 이슈 | GitHub Issues + Projects | 사용 안 함. 커밋에 FR 번호, 할 일은 `docs/roles/` 체크박스 |
| 선행 의존성 | `WORK-ORDER.md`의 🔴/🟡 그래프, AI가 선행 검사 후 정지 | **폐지.** Phase 0 계약·스텁으로 선행 의존성 자체를 제거 |
| 모듈 간 참조 | 소비자가 자기 패키지에 인터페이스 정의 | **`contract/` 패키지에 집중, 동결**, 이승민 관리 |
| 엔티티 관계 | 명시 없음 | **모듈 간 ID 참조만.** ArchUnit이 import 검사 |
| DB 스키마 | Flyway 마이그레이션, 임승현 단독 관리, `ddl-auto: validate` | **`ddl-auto: update`**, 각자 자기 엔티티. 운영 전환 시 `schema.sql` |
| WebSocket | `/ws/queue`(이승민) + `/ws/chat`(임승현), STOMP 설정 공유 | **`realtime/` 모듈 하나, 임승현.** 단일 `/ws` 엔드포인트 |
| Security 설정 | `config/`(이승민) + 필터(임승현) 협의 | **`auth/`(임승현)로 통합** |
| 설정 파일 | `application.yml` 공용 | `domain/{모듈}.yml` 사람별 분리 |
| 파트 분배 | 1차·2차 도메인(시기별 인수), 프론트 1인 + 지도 인수 | **5파트 고정 20%.** 요청·대기 화면을 송준호가, 이력·통계 백엔드를 서준이, WebSocket 전체를 임승현이 |
| 수락 단계 | P1 | P1 유지. **P0는 자동 성사**로 M1 단순화 |
| 로드맵 | 8월 ~ 12월 (5개월) | **9/21 ~ 12월 중순 (12주), Phase 0~3** |
| 회의 | 화·금 스크럼, 격주 스프린트 | **주 1회 통합 점검 30분** + 월말 데모 데이 |
