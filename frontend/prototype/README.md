# 가치가 — Phase 0 독립 화면 시안

팀원이 제공할 파일 없이 검토할 수 있는 **프론트 화면 시안**입니다. 2026-09-19의 PRD v2와 오승원 역할표를 바탕으로 작성했습니다. React 18, TypeScript, Vite, Tailwind CSS를 사용합니다.

이 폴더는 향후 팀에서 만들 `frontend/src`와 분리되어 있습니다. 현재 API 스펙을 대신하거나 Phase 1 기능 구현을 완료한 것으로 간주하지 않습니다.

## 가장 간단히 화면 보기

전달된 `WE-Meet-Preview.html`을 내려받아 브라우저로 열면 됩니다. 별도 설치나 인터넷 연결 없이 사용할 수 있는 단일 HTML입니다. 왼쪽 메뉴, 휴대폰에서는 오른쪽 위 메뉴 버튼으로 화면을 전환합니다.

## VS Code에서 코드 실행

Node.js 24를 설치하고, VS Code에서 프로젝트를 연 뒤 터미널에 입력합니다.

```bash
cd frontend/prototype
npm ci
npm run dev
```

터미널에 표시된 주소(기본 `http://localhost:5173`)를 브라우저에서 엽니다. 압축파일 안의 `prototype` 폴더 자체를 VS Code로 열었다면 첫 번째 `cd`는 생략합니다.

수정한 코드를 확인하려면:

```bash
npm run lint
npm run build
```

빌드 결과는 `dist/`에 생깁니다. `dist/WE-Meet-Preview.html`에는 CSS와 JavaScript가 포함되어 있어 그대로 다른 사람에게 전달해도 됩니다.

## 완성한 범위

| 화면      | 독립적으로 확인할 수 있는 것                                           |
| --------- | ---------------------------------------------------------------------- |
| 로그인    | 입력창, 비밀번호 표시 전환, 기본 HTML 입력 형식 확인, 다음 화면 이동   |
| 회원가입  | 이메일 → 인증번호 → 프로필의 화면 이동, 성별 선택, 선택 항목 펼치기    |
| 매칭 성사 | 예시 분담액·절감액, 동승자 카드, 지도 자리, 완료 안내 모달             |
| 정산 상세 | PRD §5.2 예시의 구간 막대, 탑승 인원, 분담액, 100원 보정 설명          |
| 채팅      | 예시 말풍선, 입력 문장을 현재 화면에만 추가해 보는 기능, 500자 입력 UI |
| 이용 이력 | 예시 내역 목록, 상세 모달, 가상 프로필                                 |

공통으로 Button, Input, Card, Badge, Spinner, Modal, BottomSheet, Toast를 제공합니다. 화면 상단의 상태 선택에서 로딩·오류·빈 화면을 확인할 수 있습니다. 회원가입 화면은 각 단계 URL로도 직접 접근할 수 있으며, 실제 인증 가드를 구현하지 않습니다.

## 현재 진행하지 않은 범위

사용자 요청에 따라 팀원 자료가 필요한 다음 단계는 구현하지 않았습니다.

- 실제 메일 발송, 코드 검증, 계정 생성, 로그인, JWT·토큰 갱신
- REST API 호출, 서버 DTO, OpenAPI 타입 생성, MSW API 핸들러
- 실제 매칭, 수락·거절, 정산 계산 엔진, 완료 처리
- 지도 SDK, 장소 검색, 경로 조회, 요청·대기 화면
- WebSocket 연결, 실제 메시지 송수신, 서버 메시지 삭제
- 실제 이용 이력·누적 통계 조회, 배포, Git 커밋·push

API 호출 코드와 `fetch`/WebSocket 연결이 없습니다. 입력 내용은 전송·영구 저장하지 않습니다. 채팅 입력은 현재 탭의 React 상태에만 남으며 새로고침하면 초기화됩니다. 회원가입의 다음 화면 버튼은 입력 형식을 확인한 뒤 화면만 이동합니다. 비밀번호는 미리보기용 임의의 값을 입력하면 됩니다.

## 코드 위치

```text
src/
  app/App.tsx                         화면 메뉴와 HashRouter
  shared/ui/index.tsx                 공통 컴포넌트
  shared/ui/Icon.tsx                  코드로 그린 SVG 아이콘
  shared/preview/fixtures.ts          문서 기반 전시용 고정 예시
  shared/preview/PreviewState.tsx     로딩·오류·빈 화면
  features/auth/AuthPage.tsx          로그인·가입 화면
  features/group/GroupPage.tsx        성사·정산 화면
  features/chat/ChatPage.tsx          채팅 화면 배치
  features/history/HistoryPage.tsx    이력·프로필 화면
  styles.css                         색상·간격·글꼴·반응형 스타일
```

## 팀 프로젝트에 가져갈 때

1. 현재는 **`frontend/prototype` 폴더만** 가져가면 됩니다. 팀의 `frontend` 전체를 교체하지 않습니다.
2. 초기 프론트 구성이 확정되면 공통 UI와 화면 디자인을 각각 본인 소유의 `src/shared/ui`, `src/features/auth`, `group`, `chat`, `history`에 옮길 수 있습니다.
3. 실제 API 연결은 이승민의 초기 구성과 `docs/api-spec.yaml` 확정 후 별도 작업으로 진행합니다. 고정 예시의 구조를 서버 DTO로 사용하지 않습니다.
4. 단일 파일 실행을 위해 현재 라우트는 `#/login`처럼 해시를 사용합니다. 팀 앱에 옮길 때는 팀 라우터에 맞춥니다.

요청·지도 담당 폴더, 팀 공용 문서, 백엔드는 변경하지 않았습니다. 역할표 체크박스도 실제 기능 완료로 표시하지 않았습니다.

자세한 화면 검토 결과는 `VERIFICATION.md`를 참고하세요.
