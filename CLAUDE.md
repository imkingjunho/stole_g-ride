# 가치가(WE-Meet) — AI 작업 규칙 (v2)

전남대 재학생 택시 동승 매칭 서비스. 5인 팀. **브랜치는 `main` 하나, 각자 자기 폴더에서만 작업하고 빌드 통과 후 직접 push 한다.** 이 파일은 그 방식이 깨지지 않도록 AI가 지켜야 할 규칙이다.

## 0. 작업 시작 전 반드시

1. **작업자 식별** — 사용자가 이름을 밝히지 않았다면 `git config user.name`을 실행해 `docs/roles/{이름}.md`를 읽는다. 일치하는 파일이 없으면 **추측하지 말고 이름을 물어본다.**
2. **`docs/roles/{이름}.md`의 가장 위 미완료 항목**이 이번 작업이다 (사용자가 다른 항목을 지정하지 않았다면).
3. **`docs/PRD.md`가 근거 문서다.** FR(§4), 알고리즘(§5), 카카오 API(§6), 데이터 모델(§7), API(§8), 구조(§9), **모듈 간 계약(§14)**을 따른다. 임의로 다른 설계를 만들지 않는다.
4. **소유 폴더 확인** — 아래 §2 표. 그 밖의 파일은 **읽기만** 한다.

## 1. 기술 스택 (변경 금지)

- Backend: Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security, WebSocket(STOMP), Spring Mail
- DB: MySQL 8.0 (`ddl-auto: update` — 마이그레이션 도구 없음) / Cache: Redis 7
- Frontend: React 18 + Vite + TypeScript, Tailwind CSS, TanStack Query, Zustand, openapi-fetch, MSW, @stomp/stompjs, react-kakao-maps-sdk
- 지도: 카카오맵 JS SDK (프론트), 카카오모빌리티 길찾기 API (백엔드 전용)
- 검사: ArchUnit(모듈 경계), GitHub Actions(빌드)

> FastAPI, Python 백엔드, Flyway/Liquibase, 다른 지도 API를 제안하거나 도입하지 않는다.

## 2. 폴더 소유 — 소유자만 수정한다

```
backend/src/main/java/com/gachiga/
├── common/     이승민   ⚠️ 공통 응답·예외·에러 코드 (요청으로만 변경)
├── config/     이승민
├── contract/   이승민   ⚠️ 동결 계약 — 모듈 간 인터페이스·DTO·이벤트 (요청으로만 변경)
├── ride/       이승민   요청, 대기열, 만료 스케줄러
├── matching/   서준     매칭 엔진, 그룹 API
├── fare/       서준     구간 분할 정산
├── stats/      서준     이력·통계
├── auth/       임승현   Security 설정, JWT, 웹메일 인증, @CurrentUser 리졸버
├── user/       임승현   회원, 프로필, 신고
├── realtime/   임승현   STOMP 설정·인증, 대기/매칭 push, 채팅
└── route/      송준호   카카오 클라이언트, 캐시, fallback, 장소 검색, 거점

backend/src/main/resources/
├── application*.yml        이승민
└── domain/{ride,matching,auth,route}.yml   각 모듈 소유자

backend/src/test/java/com/gachiga/{패키지}/   그 패키지 소유자

frontend/
├── features/request/, features/map/   송준호
└── 그 외 전체 (package.json 포함)      오승원
frontend/src/generated/                 자동 생성 — 손으로 수정 금지

루트: build.gradle, docker-compose.yml, .env.example, .gitignore, .github/, .claude/   이승민
docs/PRD.md, docs/api-spec.yaml, README.md, CLAUDE.md                          이승민
docs/roles/{이름}.md                                                            본인
```

**소유 폴더 밖 파일을 고쳐야 한다고 판단되면 고치지 않는다.** 대신 아래 형식의 요청 문구를 출력하고, 자기 폴더 안에서 가능한 부분까지만 진행한다.

```
📨 단톡방에 보낼 요청
[계약 변경 요청] {이름} / {파일} / {무엇을} / {왜}
```

예외: **Phase 0 기간(9/21~27)의 이승민**은 뼈대 구축을 위해 다른 사람 폴더에 스텁·placeholder를 만든다 (`docs/roles/이승민.md` T0-*).

## 3. 계약(`contract/`) 규칙

- `contract/`는 **읽기 전용**이다. 여기 있는 port 인터페이스·DTO·이벤트·`@CurrentUser`만으로 다른 모듈과 소통한다.
- 다른 모듈의 기능이 필요하면: `contract/`에 port가 있는지 본다 → 있으면 주입받아 쓴다 → 없으면 §2 요청 문구를 출력한다. **자기 패키지에 인터페이스를 새로 만들어 우회하지 않는다.**
- **스텁 교체:** 자기 폴더 안의 `Stub*`·`Dev*`·`InMemory*`·`Static*` 클래스를 진짜 구현으로 바꿀 때는 스텁을 삭제한다. 둘을 같이 두려면 진짜에 `@Primary`. (`route/EstimatedRouteProvider`는 예외 — fallback으로 유지)
- 이벤트는 `ApplicationEventPublisher`로 발행, `@TransactionalEventListener(phase = AFTER_COMMIT)`로 수신. 수신 측은 try/catch로 감싸 발행 측을 깨뜨리지 않는다.

## 4. 모듈 경계 — 어기면 빌드가 실패한다

1. **다른 도메인 패키지를 `import` 하지 않는다.** 허용: `com.gachiga.contract.*`, `com.gachiga.common.*`. (`common/ArchitectureTest`가 검사)
2. **엔티티는 다른 모듈의 엔티티를 JPA 관계(`@ManyToOne` 등)로 참조하지 않는다.** `Long userId`처럼 ID만 저장한다. 사용자 정보가 필요하면 `UserPort`.
3. 스케줄러·설정(`domain/{모듈}.yml`)·테이블은 각 모듈이 자기 것만 가진다.

## 5. 코딩 컨벤션

### Java
- Google Java Style, Lombok 사용. 엔티티는 `@NoArgsConstructor(access = PROTECTED)`, setter 대신 의도가 드러나는 메서드
- **문자열 비교는 `.equals()`. `==` 금지** (팀 명시 제약)
- **문자열 파싱은 `StringTokenizer`** (팀 명시 제약)
- 금액은 `int`(원) 또는 `BigDecimal`. **`double`/`float` 금지** (좌표·거리·비율은 `double` 허용)
- 예외는 `common/exception`의 `BusinessException(ErrorCode)`. `RuntimeException` 직접 throw 금지. 새 에러 코드가 필요하면 `[에러코드 요청]`
- 컨트롤러는 항상 `ApiResponse<T>` 반환. 현재 사용자는 `@CurrentUser Long userId`
- Service는 인터페이스 없이 클래스 단일 구현. 인터페이스는 `contract/`의 port만
- 설정값은 `domain/{모듈}.yml`의 `gachiga.{모듈}.*` 아래에. `application.yml`에 넣지 않는다

### TypeScript / React
- 함수형 컴포넌트, 명명 export, **`any` 금지**
- API 타입은 `src/generated/api.d.ts`만 사용. 손으로 타입 중복 정의 금지. 스펙에 없는 필드가 필요하면 `[계약 변경 요청]`
- 서버 상태 TanStack Query, 클라이언트 상태 Zustand. 공통 UI는 `shared/ui`(오승원) 것만
- 모바일 우선(360px), 터치 44px

### 공통
- 주석·커밋 메시지는 한국어, 식별자는 영어
- 요구되지 않은 라이브러리를 추가하지 않는다 (`[의존성 요청]`)

## 6. Git — `main` 직접 push

- **커밋·push는 사용자 확인 후에만 실행한다.** 임의로 커밋하지 않는다.
- 커밋 전에 반드시 빌드: 백엔드 `cd backend && ./gradlew build`, 프론트 `cd frontend && npm run lint && npm run build`. **실패 상태로 커밋을 제안하지 않는다.**
- 커밋 메시지: `{type}({모듈}): {내용} (FR-XX)` — `feat` / `fix` / `refactor` / `test` / `docs` / `chore`. 모듈은 패키지명 또는 `fe-{feature}`
- 순서: `git add -A` → `git status`로 **소유 폴더 파일만인지 확인**(아니면 `git restore --staged`) → `commit` → `git pull --rebase origin main` → (받아진 게 있으면 재빌드) → `git push origin main`
- 브랜치를 만들지 않는다. `develop`은 없다.
- 충돌이 나면 양쪽 변경을 모두 살리는 방향으로 해결하고, 공용 파일 충돌이면 사용자에게 이승민에게 알리라고 안내한다.

## 7. 테스트

- 순수 로직(정산·필터·클러스터링·마스킹)은 반드시 단위 테스트. **정산은 `Σ share == 총 요금` 검증 필수** (PRD §5.2)
- `matching`·`fare` 커버리지 80% 이상
- 외부 API(카카오·메일)는 Mock. 테스트에서 실제 호출 금지
- 테스트는 DB 없이 돌아야 한다(CI에 DB 없음). DB 필요한 검증은 `local` 프로파일로 수동

## 8. 보안

- 카카오 REST 키·JWT 시크릿·메일 비밀번호는 **환경변수에만**. 코드·문서·`.env.example`에 실제 값 금지
- 프론트 코드에 REST 키 노출 금지 (JS 키만, 도메인 제한)
- 실명·전화번호를 저장하는 코드를 작성하지 않는다

## 9. 하지 말 것

- 택시 호출·배차·결제 기능 (법적 범위 밖, PRD §2.2)
- 정산 공식을 단순 1/N으로 바꾸는 것 (구간 분할이 핵심 차별점)
- 알고리즘(§5) 임의 단순화
- 기존 코드 대규모 리팩터링 (요청받은 범위만)
- 남의 폴더에 "도와주려고" 코드 작성

## 10. 작업을 끝냈으면

1. 빌드 명령과 결과를 보여 준다.
2. `docs/roles/{이름}.md`의 해당 항목을 `[x]`로 바꿀지 묻고, 바꾼다.
3. 커밋 메시지를 제안하고, 사용자 확인 후 §6 순서로 진행한다.
4. 계약·공용 파일 변경이 필요했다면 요청 문구를 다시 한 번 출력한다.
5. 스텁을 사용해 우회한 부분이 있으면 "나중에 교체할 지점"을 알린다.
