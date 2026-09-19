# 가치가 (WE-Meet) — 팀원 사용 설명서

> 전남대 재학생 택시 동승 매칭 서비스 · 5인 팀
> **코딩이 처음이어도 괜찮습니다. 이 문서 순서대로만 따라 하세요.**
> 무엇을 만드는지는 `docs/PRD.md`, 내가 오늘 할 일은 `docs/roles/내이름.md`.

[![CI](https://github.com/imkingjunho/stole_g-ride/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/imkingjunho/stole_g-ride/actions/workflows/ci.yml)

> 위 배지가 **빨간색**이면 `main`이 깨진 상태입니다 → 3단계 "GitHub에 빨간 X가 떴어요". 배지를 누르면 어떤 커밋이 깨졌는지 보입니다.

---

## 📌 우리가 일하는 방식 (한 장 요약)

브랜치는 **`main` 하나**입니다. 각자 **자기 폴더**에서만 작업하고, **빌드가 통과하면** main에 바로 올립니다.

```
1. 최신 코드 받기        →  git pull --rebase origin main
2. 내 폴더에서 작업       →  AI에게: "나는 OOO이야. docs/roles/OOO.md 읽고 다음 작업 진행해줘."
3. 빌드 확인             →  ./gradlew build  또는  npm run lint && npm run build   (초록이어야 다음으로)
4. 올리기                →  git add -A → git status(내 폴더만인지 확인) → git commit → git pull --rebase → git push
```

**핵심 원칙 딱 3개**

1. **내 폴더만 만진다.** (내 폴더가 어딘지 → 아래 4번 표)
2. **빌드 통과 전에는 올리지 않는다.**
3. **공용 파일(`contract/`, `common/`, `api-spec.yaml`, `build.gradle`, `.env.example` 등)은 이승민에게 카톡으로 요청한다.** 직접 고치지 않는다.

이 세 가지만 지키면 **충돌도, PR도, 리뷰 대기도 없습니다.** 왜 그런지는 `docs/PRD.md` §13.2.

---

## 1단계. 처음 딱 한 번만 하는 준비

### 1-1. 프로그램 설치

| 프로그램 | 용도 | 받는 곳 |
|---|---|---|
| **Git** | 코드 버전 관리 | git-scm.com |
| **JDK 17** | 백엔드 실행 (Java) | Temurin 17 검색 |
| **Node.js 24** | 프론트엔드 실행 | nodejs.org (LTS) |
| **Docker Desktop** | DB 실행 | docker.com |
| **IntelliJ IDEA** (Community) | 백엔드 코딩 | jetbrains.com |
| **VS Code** | 프론트엔드 코딩 | code.visualstudio.com |
| **GitHub Desktop** (선택) | 터미널 대신 클릭으로 git | desktop.github.com |

> 백엔드(이승민·서준·임승현·송준호) → JDK + IntelliJ
> 프론트엔드(오승원·송준호) → Node + VS Code
> 송준호는 둘 다.

**설치 확인:** 터미널(Mac) 또는 PowerShell(Windows)에서

```bash
git --version      # git version 2.x
java -version      # 17
node -v            # v24.x
docker -v          # 버전 나오면 OK
```

### 1-2. Git에 내 이름 등록 (중요!)

```bash
git config --global user.name "이승민"        # ← 본인 이름. docs/roles/ 파일명과 똑같이!
git config --global user.email "본인@jnu.ac.kr"
```

> 이름이 정확해야 (1) 커밋에 누가 올렸는지 남고 (2) AI가 `git config user.name`으로 여러분을 알아봅니다.

### 1-3. 코드 내려받기

```bash
cd Desktop
git clone https://github.com/imkingjunho/stole_g-ride.git
cd stole_g-ride
```

> GitHub 로그인을 물어보면 비밀번호 대신 **Personal Access Token**이 필요합니다. 어렵다면 **GitHub Desktop**으로 clone 하세요.

### 1-4. 실행해 보기

```bash
cp .env.example .env          # Windows: copy .env.example .env
```

`.env`를 열어 카카오 키를 넣습니다. **키는 단톡방이 아니라 별도 채널로 받으세요.** (키 없이도 백엔드는 뜹니다 — 경로가 "추정치"로 나올 뿐입니다.)

```bash
docker compose up -d --wait   # MySQL + Redis. Docker Desktop이 켜져 있어야 함
```

**백엔드**
```bash
cd backend
./gradlew bootRun             # Windows: gradlew.bat bootRun
```
→ `http://localhost:8080/swagger-ui.html` 열리면 성공 ✅

**프론트엔드**
```bash
cd frontend
cp .env.example .env          # 처음 한 번만 (Windows: copy .env.example .env)
npm install                   # 처음 한 번만
npm run dev
```
→ `http://localhost:5173` 열리면 성공 ✅

기본은 **가짜 서버(MSW) 모드**라 백엔드를 켜지 않아도 모든 화면이 돕니다. 실서버에 붙이려면 `frontend/.env`의 `VITE_API_MODE=real`로 바꾸세요. (루트 `.env`는 Docker용이라 다른 파일입니다.)

---

## 2단계. 매일 하는 작업 루틴

### ① 최신 코드 받기 (시작할 때 항상!)

```bash
git pull --rebase origin main
```

> 이걸 안 하면 나중에 push가 거절됩니다. 습관으로 만드세요.

### ② AI에게 작업 시키기

IntelliJ / VS Code에서 AI 어시스턴트를 열고 **이렇게 복붙**하세요.

```
나는 이승민이야. docs/roles/이승민.md 읽고 내 역할의 다음 미완료 작업을 진행해줘.
```

> ⚠️ 이름을 꼭 말하세요. "내 역할 구현해줘" ❌ / "나는 이승민이야, …" ✅

| 하고 싶은 것 | 이렇게 말하세요 |
|---|---|
| 다음 작업 시작 | `나는 OOO이야. docs/roles/OOO.md 읽고 다음 미완료 작업 진행해줘.` |
| 특정 작업 지정 | `나는 OOO이야. docs/roles/OOO.md의 T1-3 작업을 해줘.` |
| 코드 이해 안 됨 | `방금 만든 코드를 코딩 초보도 알아듣게 한 줄씩 설명해줘.` |
| 에러가 났을 때 | `이 에러가 났어. 원인과 고치는 법 알려줘. (에러 메시지 전체 붙여넣기)` |
| 테스트 추가 | `방금 만든 기능의 테스트 코드를 작성해줘. docs/PRD.md 기준으로.` |
| 계약이 부족할 때 | `이 작업에 필요한 계약 변경 요청 문구를 써줘.` → 나온 문구를 단톡방에 |
| 커밋 메시지 | `방금 작업 내용으로 커밋 메시지를 써줘.` |

### ③ 내가 직접 확인하기 ← **절대 생략 금지**

```
□ 서버가 정상적으로 실행되나?
□ 기능이 실제로 동작하나? (Swagger나 브라우저에서 눌러보기)
□ 이 코드가 뭘 하는지 내가 설명할 수 있나?
```

> 세 번째가 제일 중요합니다. 발표와 면접에서 "이건 왜 이렇게 했어요?"가 반드시 나옵니다.

### ④ 빌드 확인

```bash
cd backend  && ./gradlew build       # 백엔드 담당자 (Windows: gradlew.bat build)
cd frontend && npm run lint && npm run build   # 프론트 담당자 (CI 도 lint → build 순서로 돈다)
```

`BUILD SUCCESSFUL` / 빌드 완료 메시지가 나와야 합니다. **빨간 글씨면 올리지 마세요.** AI에게 에러를 붙여 물어보세요.

### ⑤ 올리기

```bash
git add -A
git status                     # ← 내 폴더 파일만 떠야 합니다. 남의 폴더가 보이면: git restore --staged 그파일
git commit -m "feat(ride): 매칭 요청 생성 API (FR-08)"
git pull --rebase origin main  # 그 사이 누가 올렸을 수 있음. 뭔가 받아졌으면 ④ 빌드 한 번 더
git push origin main
```

**커밋 메시지 규칙:** `타입(모듈): 내용 (FR-번호)`
```
feat(fare): 구간 분할 정산 계산기 (FR-16)
fix(route): 캐시 키 좌표 반올림 누락 수정 (FR-15)
feat(fe-group): 매칭 성사 화면 절감액 표시 (FR-17)
docs(roles): 송준호 T1-2 완료 체크
```
타입: `feat` 새 기능 / `fix` 버그 / `test` 테스트 / `docs` 문서 / `refactor` 정리 / `chore` 설정

**GitHub Desktop을 쓰면:** `Fetch origin` → `Pull origin` → (터미널에서 ④ 빌드) → 왼쪽 변경 목록이 내 폴더만인지 확인 → Summary에 메시지 → `Commit to main` → `Push origin`

**얼마나 자주?** 하루 한 번 이상, 빌드가 통과하는 단위로. 반쯤 만든 것도 컴파일만 되면 올려도 됩니다. **3일 넘게 안 올리면 위험 신호**입니다.

### ⑥ 끝나면 체크

`docs/roles/내이름.md`에서 끝낸 항목을 `[x]`로 바꾸고 같이 올립니다. 그게 팀에게 "이거 됐어요" 신호입니다.

---

## 3단계. 자주 생기는 문제

### "push가 거절됐어요" (`rejected`, `non-fast-forward`)
누가 먼저 올린 겁니다. 정상입니다.
```bash
git pull --rebase origin main
git push origin main
```

### "충돌(CONFLICT)이 났어요"
내 폴더만 만졌다면 원칙상 안 납니다. 났다면 공용 파일을 누가 건드린 것.
```
AI에게: "충돌 난 파일들 정리해줘. 양쪽 변경사항 다 살려야 해."
그다음: git add 그파일  →  git rebase --continue
```
모르겠으면 `git rebase --abort` 하고 단톡방에 물어보세요. 되돌아갑니다.

### "GitHub에 빨간 X가 떴어요" (CI 실패)
main이 깨진 겁니다. **깨뜨린 사람(커밋에 이름이 있음)이 30분 안에 고쳐 올립니다.** 연락이 안 되면 누구든:
```bash
git revert <커밋해시>      # 그 커밋만 되돌리는 새 커밋
git push origin main
```
revert는 비난이 아니라 복구입니다. 되돌려진 사람은 로컬에서 고쳐 다시 올리면 됩니다.

### "실수로 남의 폴더를 고쳤어요"
- push 전: `git restore 파일경로` (그 파일만) / `git restore .` (전부, 커밋 전)
- push 후: 단톡방에 알리고 폴더 주인이 판단합니다.

### "AI가 contract/ 나 api-spec.yaml 을 고치려고 해요"
멈추고 `git restore`. 그 파일은 이승민만 고칩니다. AI에게 "계약 변경 요청 문구 써줘" → 단톡방.

### "DB가 이상해요 / 컬럼이 안 맞아요"
누가 컬럼을 지우거나 이름을 바꿨을 때 생깁니다(공지가 있었을 것).
```bash
docker compose down -v && docker compose up -d --wait     # DB 초기화
```

### "docker compose up이 안 돼요" → Docker Desktop이 켜져 있는지 확인
### "gradlew: permission denied" (Mac) → `chmod +x gradlew`
### "포트가 이미 사용 중" → 이전에 켠 서버가 돌고 있음. `Ctrl + C`로 끄고 다시
### "npm install 에러" → `rm -rf node_modules package-lock.json && npm install`

---

## 4단계. 내 폴더는 어디?

| 이름 | 파트 | 내 폴더 (여기만 수정) |
|---|---|---|
| **이승민** | ① 플랫폼·요청 | `backend/…/common/ config/ contract/ ride/`, `application*.yml`, `domain/ride.yml`, 루트 파일(`build.gradle` `docker-compose.yml` `.env.example` `.github/`), `docs/PRD.md` `docs/api-spec.yaml` `README.md` `CLAUDE.md` |
| **서준** | ② 매칭·정산·통계 | `backend/…/matching/ fare/ stats/`, `domain/matching.yml` |
| **임승현** | ③ 계정·실시간 | `backend/…/auth/ user/ realtime/`, `domain/auth.yml` |
| **송준호** | ④ 경로·지도·요청 화면 | `backend/…/route/`, `domain/route.yml`, `frontend/src/features/request/`, `frontend/src/features/map/` |
| **오승원** | ⑤ 프론트 기반·핵심 화면 | `frontend/` 전체 (단 `features/request/`·`features/map/` 제외) |
| 전원 | | `docs/roles/내이름.md`, `backend/src/test/java/com/gachiga/내패키지/` |

`backend/…/` = `backend/src/main/java/com/gachiga/`

**공용 파일 — 이승민만 수정. 필요하면 단톡방에 한 줄:**
```
[계약 변경 요청] 서준 / contract/route/RouteResult / sections에 duration 추가 / 도착 예정 시각 계산에 필요
[에러코드 요청]  임승현 / ErrorCode / VERIFY_CODE_EXPIRED 추가 / 만료와 불일치 구분
[의존성 요청]    송준호 / build.gradle / spring-boot-starter-cache / 경로 캐시
[.env 항목 요청] 임승현 / .env.example / JWT_SECRET, MAIL_USERNAME, MAIL_PASSWORD
```
이승민은 24시간 안에 반영하고 "반영했음, pull 하세요"라고 공지합니다. `frontend/package.json`은 오승원에게 같은 방식으로.

---

## 🚫 절대 하지 말 것

| 하지 말 것 | 이유 |
|---|---|
| `.env` 커밋 | **카카오 키 유출.** `.gitignore`에 있지만 `git add -A` 후 `git status`로 확인 |
| 빌드 안 되는 코드 push | 5명 전부의 작업이 멈춥니다 |
| 남의 폴더 수정 | 그 사람 작업이 덮어씌워집니다 |
| `contract/`, `common/`, `api-spec.yaml`, `build.gradle` 직접 수정 | 이승민에게 요청 |
| 다른 패키지 클래스 `import` (예: `matching`에서 `ride.RideRequest`) | 빌드가 실패합니다(ArchUnit). `contract`의 인터페이스를 쓰세요 |
| 이해 못 한 코드 push | 나중에 고칠 수도, 설명할 수도 없습니다 |
| 실제 API 키를 AI 채팅에 붙여넣기 | 키 유출 |

---

## 📚 문서 어디에 뭐가 있나

| 파일 | 내용 | 언제 보나 |
|---|---|---|
| `README.md` | 이 문서 | 막힐 때마다 |
| `docs/PRD.md` | 제품 명세 + 파트 분배(§12) + 협업 방식(§13) + 모듈 간 계약(§14) + 로드맵(§15) | "이 기능 뭐 만드는 거지?", "이건 누구 담당?" |
| `docs/roles/내이름.md` | **내 작업 체크리스트** | 매번 작업 시작할 때 |
| `docs/api-spec.yaml` | API 계약 (Phase 0 이후) | 프론트-백엔드 연동할 때 |
| `CLAUDE.md` | AI 규칙 | 볼 일 없음 (AI가 알아서 읽음) |

---

## 🗓️ 팀 리듬

| 언제 | 무엇 |
|---|---|
| **매주 1회, 30분, 온라인** (요일·시간은 팀에서 정함) | **통합 점검** — 전원이 그 자리에서 `git pull` → 실행 → 각자 자기 파트 3분 시연 → 막힌 것·요청할 것. 진행: 서준 |
| 매월 마지막 통합 점검 | **데모 데이** — 각자 5분 시연 + 다음 달 목표 |
| 수시 | 단톡방. **막히면 3시간 안에 말하세요.** 혼자 이틀 붙잡는 게 팀에 가장 손해입니다 |

**일정 (자세한 건 PRD §15)**

| 단계 | 기간 | 목표 |
|---|---|---|
| Phase 0 뼈대 | 9/21 ~ 9/27 | 이승민이 뼈대·계약·스텁·CI. 나머지는 환경 세팅 + 첫 push 연습 |
| Phase 1 P0 | 9/28 ~ 10/25 | 각자 핵심 기능. **10/25 실서버로 시나리오 한 바퀴(M1)** |
| Phase 2 P1 | 10/26 ~ 11/22 | 수락/거절, 실시간, 이력, 완성도 |
| Phase 3 마무리 | 11/23 ~ 12/13 | 베타 30명, 부하 테스트, 백서. 12/13 코드 프리즈 |
| 최종 발표 | 12월 3주 | |

---

## ✅ 첫 주(9/21~27) 할 일 — 전원

```
□ 1단계 프로그램 전부 설치
□ git config로 내 이름 등록 (docs/roles/ 파일명과 동일하게)
□ clone → docker compose up → 백엔드 실행 성공 (Swagger 확인)
□ docs/PRD.md §12(내 파트)·§13(올리는 법)·§14(계약) 읽기
□ docs/roles/내이름.md 읽고 Phase 0 항목 진행
□ 첫 push 연습: docs/roles/내이름.md 의 "환경 세팅 완료" 체크박스를 [x]로 바꿔 2단계 ⑤ 순서대로 main에 올려 보기
□ 이승민 "Phase 0 끝" 공지 후 Phase 1 시작
```

마지막 항목이 **push 연습**입니다. 실제 코드 전에 흐름을 한 번 익혀 두면 훨씬 수월합니다.
