# WE-Meet (가치가) — AI 작업 규칙

전남대 재학생 대상 택시 동승 매칭 서비스. 5인 팀 협업 프로젝트.

## 0. 작업 시작 전 반드시

1. **작업자 식별** — 사용자가 이름을 밝히지 않았다면 `git config user.name`을 실행하고 `docs/roles/{이름}.md`를 읽는다. 일치하는 파일이 없으면 **추측하지 말고 사용자에게 이름을 물어본다.**
2. **`docs/PRD.md` 참조** — 기능 요구사항(FR-XX), 알고리즘 명세(§5), 카카오 API 스펙(§6), 데이터 모델(§7)의 근거 문서다. 임의로 다른 설계를 만들지 않는다.
3. **담당 범위 확인** — `docs/roles/{이름}.md`에 명시된 패키지 밖은 수정하지 않는다.
4. **선행 작업 확인** — `docs/WORK-ORDER.md`에서 이번 작업의 선행 항목을 확인한다. 아래 §0.1을 따른다.

### 0.1 선행 작업 검사 (작업 순서 역전 방지)

이 팀은 5명이 병렬로 작업하므로 **순서가 뒤바뀌면 양쪽 다 재작업**하게 된다.
코드를 한 줄이라도 쓰기 전에 다음을 수행한다.

```
1) docs/WORK-ORDER.md §2 선행표에서 지금 작업의 선행 항목과 등급(🔴/🟡)을 찾는다
2) 선행 항목 담당자의 docs/roles/{이름}.md에서 해당 체크박스를 확인한다
3) [ ] 미완료이면, 체크 누락일 수 있으므로 실제 산출물 파일이 있는지 확인한다
      (WORK-ORDER.md §4에 항목별 확인 경로가 있다)
4) 그래도 없으면 등급에 따라 처리한다
```

| 등급 | 처리 |
|---|---|
| 🔴 하드 블로커 | **코드를 작성하지 않고 즉시 멈춘다.** `WORK-ORDER.md` §5 형식으로 "하려던 작업 / 막고 있는 선행 / **먼저 해야 할 사람** / 이유 / 대안"을 알린다 |
| 🟡 소프트 블로커 | 멈추지 않는다. Stub·Mock으로 우회해 진행하고, **우회했다는 사실과 나중에 교체할 지점**을 반드시 알린다 |

- 선행이 미완료인데 🔴을 무시하고 진행하지 않는다. 사용자가 "그냥 진행해"라고 하면 그때 진행하되, 나중에 재작업이 필요한 지점을 먼저 알린다.
- 작업을 끝냈으면 `docs/roles/{이름}.md`의 체크박스를 `[x]`로 갱신하라고 사용자에게 알린다. **그게 다른 팀원의 출발 신호다.**

## 1. 기술 스택 (변경 금지)

- Backend: Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, WebSocket(STOMP)
- DB: MySQL 8.0 / Cache: Redis 7
- Frontend: React 18 + Vite + TypeScript, Tailwind CSS, TanStack Query, Zustand
- 지도: 카카오맵 JS SDK (프론트), 카카오모빌리티 길찾기 API (백엔드 전용)
- 빌드: Gradle / npm

> FastAPI, Python 백엔드, 다른 지도 API를 제안하거나 도입하지 않는다.

## 2. 패키지 구조와 오너십

```
backend/src/main/java/com/gachiga/
├── auth/       임승현    인증, JWT
├── user/       임승현    회원, 프로필
├── chat/       임승현    WebSocket 채팅
├── ride/       이승민    매칭 요청, 대기열, 스케줄러
├── common/     이승민    ⚠️ 공통 응답·예외·설정 (승인 없이 수정 금지)
├── matching/   서준      매칭 엔진
├── fare/       서준      구간 분할 정산
├── route/      송준호    카카오 API 클라이언트
└── stats/      송준호    이력, 통계

frontend/src/
├── features/map/    송준호   지도·경로 시각화
└── (그 외 전체)      오승원   화면, 라우팅, 디자인 시스템
```

## 3. 절대 수정 금지 (담당자 승인 필요)

| 경로 | 오너 |
|---|---|
| `common/response/`, `common/exception/`, `config/` | 이승민 |
| `docs/api-spec.yaml` | 서준 (중재) |
| `db/migration/*.sql` | 임승현 |
| `build.gradle`, `package.json` | 이승민 / 오승원 |
| `.env.example` | 항목 추가만 가능, 값은 placeholder만 |

이 파일들을 수정해야 한다면 **직접 고치지 말고 사용자에게 "담당자에게 요청이 필요하다"고 알린다.**

## 4. 다른 패키지의 기능이 필요할 때

남의 패키지에 코드를 작성하지 않는다. 대신:

1. 필요한 기능을 **자기 패키지 안에 인터페이스로 정의**한다
2. 테스트용 Stub 구현으로 개발을 이어간다
3. 실제 구현이 필요하다고 사용자에게 알린다

```java
// ✅ matching 패키지 안에 정의
public interface RouteProvider {
    RouteResult findRoute(Coordinate origin, List<Coordinate> waypoints, Coordinate dest);
}
// 개발 중에는 FixedRouteProvider(고정값 반환)로 진행
```

## 5. 코딩 컨벤션

### Java
- Google Java Style
- **문자열 비교는 반드시 `.equals()`. `==` 금지** (팀 명시 제약)
- **문자열 파싱은 `StringTokenizer` 사용** (팀 명시 제약)
- 금액 계산은 `int`(원 단위) 또는 `BigDecimal`. **`double`, `float` 금지**
- 예외는 `common/exception`의 커스텀 예외 사용. `RuntimeException` 직접 throw 금지
- Lombok 사용. 엔티티는 `@NoArgsConstructor(access = PROTECTED)`
- Service는 인터페이스 없이 클래스 단일 구현 (과도한 추상화 금지)

### TypeScript / React
- 함수형 컴포넌트, 명명 export
- API 타입은 `docs/api-spec.yaml`에서 생성 (`openapi-typescript`). 손으로 타입 중복 정의 금지
- 서버 상태는 TanStack Query, 클라이언트 상태는 Zustand
- `any` 금지

### 공통
- 주석과 커밋 메시지는 한국어
- 변수·함수·클래스명은 영어

## 6. Git

```
브랜치: {type}/#{이슈번호}-{영문-요약}
        예) feat/#12-matching-engine

커밋:   feat: 구간 분할 정산 로직 구현 (#12)
        타입: feat / fix / refactor / test / docs / chore
```

- `main`, `develop`에 직접 push 금지
- **커밋과 push는 사용자 확인 후 실행한다.** 임의로 커밋하지 않는다
- PR 생성 전 반드시 로컬 빌드·테스트 통과 확인

## 7. 테스트

- `matching`, `fare` 패키지는 커버리지 80% 이상
- **정산 로직은 `Σ share_i == 총 요금` 검증 테스트를 반드시 포함**한다 (PRD §5.2)
- 외부 API 호출은 Mock. 실제 카카오 API를 테스트에서 호출하지 않는다

## 8. 보안

- 카카오 REST API 키는 **백엔드 환경변수에만** 존재. 프론트 코드에 절대 노출 금지
- 실제 키 값을 코드·문서·커밋에 포함하지 않는다
- 개인정보 최소 수집 원칙: 실명·전화번호를 저장하는 코드를 작성하지 않는다

## 9. 하지 말 것

- 택시 호출·배차·결제 기능 구현 (법적 범위 밖, PRD §2.2)
- 요구되지 않은 라이브러리 추가
- 기존 코드 대규모 리팩터링 (요청받은 범위만 작업)
- 정산 공식을 단순 1/N으로 바꾸는 것 (구간 분할이 핵심 차별점)
