# WE-Meet (가치가) — 팀원 사용 설명서

> 전남대 재학생 택시 동승 매칭 서비스
> **코딩이 처음이어도 괜찮습니다. 이 문서 순서대로만 따라 하세요.**

---

## 📌 우리가 일하는 방식 (한 장 요약)

```
1. 최신 코드 받기        →  git pull
2. 내 작업 방 만들기      →  git switch -c feat/#12-...
3. AI에게 시키기          →  "나는 OOO이야. docs/roles/OOO.md 읽고 다음 작업 진행해줘."
4. 내가 확인하기          →  실행해 보고, 코드 읽어보기
5. 올리기                →  commit → push → PR → 리뷰 받기
```

**핵심 원칙 딱 3개**
1. `main`, `develop`에 직접 올리지 않는다 (항상 내 브랜치 → PR)
2. 내 담당 폴더 밖은 건드리지 않는다
3. 내가 설명 못 하는 코드는 올리지 않는다

---

## 1단계. 처음 딱 한 번만 하는 준비

### 1-1. 프로그램 설치

| 프로그램 | 용도 | 받는 곳 |
|---|---|---|
| **Git** | 코드 버전 관리 | git-scm.com |
| **JDK 17** | 백엔드 실행 (Java) | Temurin 17 검색 |
| **Node.js 20** | 프론트엔드 실행 | nodejs.org (LTS 버전) |
| **Docker Desktop** | DB 실행 | docker.com |
| **IntelliJ IDEA** | 백엔드 코딩 (Community 무료) | jetbrains.com |
| **VS Code** | 프론트엔드 코딩 | code.visualstudio.com |

> 백엔드(서준·이승민·임승현·송준호) → IntelliJ
> 프론트엔드(오승원·송준호) → VS Code

**설치 확인:** 터미널(Mac) 또는 PowerShell(Windows)에 하나씩 쳐보세요.

```bash
git --version      # git version 2.x  나오면 OK
java -version      # 17 나오면 OK
node -v            # v20.x 나오면 OK
docker -v          # 버전 나오면 OK
```

### 1-2. Git에 내 이름 등록 (중요!)

```bash
git config --global user.name "이승민"        # ← 본인 이름 (역할 파일명과 똑같이!)
git config --global user.email "본인@jnu.ac.kr"
```

> 이름을 정확히 넣어야 AI가 "아, 이 사람이 이승민이구나" 하고 알아볼 수 있습니다.

### 1-3. 코드 내려받기

```bash
cd Desktop                                    # 원하는 위치로 이동
git clone https://github.com/팀계정/we-meet.git
cd we-meet
```

> GitHub 로그인을 물어보면: 비밀번호 대신 **Personal Access Token**이 필요합니다.
> 어렵다면 **GitHub Desktop** 앱을 쓰세요. 클릭 몇 번으로 됩니다.

### 1-4. 실행해 보기

```bash
cp .env.example .env      # 설정 파일 복사 (Windows: copy .env.example .env)
```

`.env` 파일을 열어서 카카오 키를 넣습니다. **키는 팀 단톡방이 아니라 별도 채널로 받으세요.**

```bash
docker compose up -d      # DB 실행 (Docker Desktop이 켜져 있어야 함)
```

**백엔드 실행**
```bash
cd backend
./gradlew bootRun         # Windows: gradlew.bat bootRun
```
→ 브라우저에서 `http://localhost:8080/swagger-ui.html` 열리면 성공 ✅

**프론트엔드 실행**
```bash
cd frontend
npm install               # 처음 한 번만
npm run dev
```
→ 브라우저에서 `http://localhost:5173` 열리면 성공 ✅

---

## 2단계. 매일 하는 작업 루틴

### ① 최신 코드 받기 (작업 시작할 때 항상!)

```bash
git switch develop
git pull
```

> **이걸 안 하면 나중에 충돌(conflict)이 나서 고생합니다.** 습관으로 만드세요.

### ② 내 작업 브랜치 만들기

브랜치 = 나만의 작업 공간. 여기서 뭘 해도 남에게 영향 없습니다.

```bash
git switch -c feat/#12-matching-engine
```

**이름 규칙**
```
feat/#이슈번호-영문요약     새 기능
fix/#이슈번호-영문요약      버그 수정
docs/#이슈번호-영문요약     문서

예) feat/#15-jwt-auth
    fix/#20-fare-rounding
```

### ③ AI에게 작업 시키기

IntelliJ나 VS Code에서 AI 어시스턴트를 열고, **이렇게 복붙**하세요.

```
나는 이승민이야. docs/roles/이승민.md 읽고 내 역할의 다음 미완료 작업을 진행해줘.
```

> ⚠️ 이름을 꼭 말해야 합니다. AI는 여러분이 누군지 자동으로 모릅니다.
> "내 역할 구현해줘" ❌ / "나는 이승민이야, ..." ✅

**상황별 복붙 문장**

| 하고 싶은 것 | 이렇게 말하세요 |
|---|---|
| 다음 작업 시작 | `나는 OOO이야. docs/roles/OOO.md 읽고 다음 미완료 작업 진행해줘.` |
| 특정 작업 지정 | `나는 OOO이야. docs/roles/OOO.md의 T2-3 작업을 해줘.` |
| 코드 이해 안 됨 | `방금 만든 코드를 코딩 초보도 알아듣게 한 줄씩 설명해줘.` |
| 에러가 났을 때 | `이 에러가 났어. 원인이 뭔지랑 어떻게 고치는지 알려줘. (에러 메시지 전체 붙여넣기)` |
| 테스트 추가 | `방금 만든 기능의 테스트 코드를 작성해줘. docs/PRD.md 기준으로.` |
| PR 설명 작성 | `방금 작업 내용으로 PR 본문을 작성해줘.` |

### ④ 내가 직접 확인하기 ← **절대 생략 금지**

```
□ 서버가 정상적으로 실행되나?
□ 기능이 실제로 동작하나? (Swagger나 브라우저에서 눌러보기)
□ 이 코드가 뭘 하는지 내가 설명할 수 있나?
```

> 세 번째가 제일 중요합니다. 설명 못 하겠으면 AI에게
> **"이 코드 한 줄씩 초보도 알게 설명해줘"** 라고 물어보세요.
> 발표할 때, 나중에 면접에서 "이건 왜 이렇게 했어요?" 질문이 반드시 나옵니다.

### ⑤ 올리기

```bash
git add .
git commit -m "feat: 매칭 요청 생성 API 구현 (#12)"
git push -u origin feat/#12-matching-engine
```

**커밋 메시지 규칙**
```
feat: 새 기능 추가 (#이슈번호)
fix: 버그 수정 (#이슈번호)
test: 테스트 추가 (#이슈번호)
docs: 문서 수정 (#이슈번호)
refactor: 코드 정리 (#이슈번호)
```

push 하면 터미널에 GitHub 링크가 뜹니다. 클릭 → **Create Pull Request** → 리뷰어 1명 지정.

### ⑥ 리뷰 받고 합치기

- 리뷰어가 Approve 하면 **Squash and merge** 버튼 클릭
- **내가 만든 PR을 내가 머지하지 않습니다** (반드시 다른 사람 확인)
- 머지 후:
```bash
git switch develop
git pull
```

---

## 3단계. 자주 생기는 문제

### "충돌(conflict)이 났어요"

```bash
git switch develop
git pull
git switch 내브랜치
git merge develop
```
그다음 AI에게: `충돌 난 파일들 정리해줘. 양쪽 변경사항 다 살려야 해.`

> 매일 아침 ①번(pull)만 해도 충돌은 거의 안 납니다.

### "docker compose up이 안 돼요"
→ Docker Desktop 앱이 실행 중인지 확인하세요.

### "gradlew: permission denied" (Mac)
```bash
chmod +x gradlew
```

### "포트가 이미 사용 중이에요"
→ 이전에 켠 서버가 아직 돌고 있습니다. 터미널에서 `Ctrl + C`로 끄고 다시 실행.

### "npm install에서 에러가 나요"
```bash
rm -rf node_modules package-lock.json
npm install
```

### "AI가 엉뚱한 파일을 고쳤어요"
```bash
git restore 파일경로        # 그 파일만 되돌리기
git restore .              # 전부 되돌리기 (커밋 전에만)
```

### "실수로 develop에 커밋했어요"
당황하지 말고 **push 하기 전에** 팀 단톡방에 물어보세요. 되돌릴 수 있습니다.

---

## 🚫 절대 하지 말 것

| 하지 말 것 | 이유 |
|---|---|
| `.env` 파일 커밋 | **카카오 API 키가 유출됩니다.** 실제 사고 많이 납니다 |
| `main`, `develop`에 직접 push | 남의 작업이 망가집니다 |
| 남의 폴더 수정 | 충돌 나고, 그 사람 작업이 날아갑니다 |
| `common/`, `build.gradle`, `db/migration/` 수정 | 담당자 승인 필요 (아래 표 참고) |
| 이해 못 한 코드 머지 | 나중에 고칠 수도, 설명할 수도 없습니다 |
| 실제 API 키를 AI 채팅에 붙여넣기 | 키 유출 |

**담당자 승인이 필요한 파일**

| 파일 | 담당 |
|---|---|
| `common/`, `config/`, `build.gradle` | 이승민 |
| `db/migration/*.sql` | 임승현 |
| `docs/api-spec.yaml` | 서준 |
| `frontend/package.json` | 오승원 |

> 이 파일들을 고쳐야 하면 **직접 고치지 말고 담당자에게 카톡**하세요.

---

## 📚 문서 어디에 뭐가 있나

| 파일 | 내용 | 언제 보나 |
|---|---|---|
| `README.md` | 이 문서 | 막힐 때마다 |
| `docs/PRD.md` | 기능 명세 전체 | "이 기능 뭐 만드는 거지?" 싶을 때 |
| `docs/roles/내이름.md` | **내 작업 목록** | 매번 작업 시작할 때 |
| `docs/api-spec.yaml` | API 규격 | 프론트-백엔드 연동할 때 |
| `CLAUDE.md` | AI 규칙 | 볼 일 없음 (AI가 알아서 읽음) |

---

## 🗓️ 팀 일정

| 언제 | 무엇 |
|---|---|
| 화요일 / 금요일 | 15분 온라인 스크럼 — 한 일 / 할 일 / 막힌 것 |
| 격주 월요일 | 스프린트 계획 — 이슈 나누기 |
| 격주 금요일 | 데모 + 회고 |
| 매달 말 | 데모 데이 — 각자 5분 시연 |

**막히면 3시간 안에 말하세요.** 혼자 이틀 붙잡고 있는 게 팀에 가장 손해입니다.

---

## ✅ 오늘 할 일 (첫 주)

```
□ 1단계 프로그램 전부 설치
□ git config로 내 이름 등록
□ 코드 clone 받고 서버 실행 성공
□ docs/roles/내이름.md 읽어보기
□ docs/PRD.md 훑어보기 (내 담당 FR 번호 찾기)
□ 테스트 브랜치 만들어서 README에 내 이름 한 줄 추가 → PR 올려보기 (연습!)
```

마지막 항목은 **PR 연습**입니다. 실제 코드 작업 전에 흐름을 한 번 익혀두면 훨씬 수월합니다.
