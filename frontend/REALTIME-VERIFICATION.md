# 실시간 연결 확인

## 이번 자동 검사의 범위

`node scripts/verify-realtime.mjs`는 Playwright와 브라우저가 준비된 개발/검사 환경에서 실행한다.
기존 verify-ui/verify-real-transport와 같은 방식이다. 추가 npm 의존성은 이번 변경에 포함하지 않았다.
`CHROMIUM_PATH`로 브라우저 실행 파일을 지정할 수 있다.

- real 모드로 Vite 5195, HTTP 대역 5196을 실행한다. MSW는 실행하지 않는다.
- 브라우저의 실제 REST 클라이언트와 STOMP 클라이언트를 사용하되, WebSocket 상대는 Playwright의 프로토콜 대역이다.
- 토큰 문자열은 가짜다. Java 서버의 JWT 서명/인가를 검사했다는 의미가 아니다.
- 실제 카카오/메일/운영 API를 호출하지 않는다. 테스트용 브라우저와 대역 데이터만 사용한다.
- 실패하면 종료 코드 1, 성공하면 항목별 PASS를 출력한다. 서버/브라우저는 종료 시 정리한다.

## 실제 Java 서버가 준비되면

루트 README에 따라 전용 개발 DB·Redis·백엔드를 켜고 가입 완료 테스트 계정을 준비한다.
기존 작업을 덮어쓰거나 운영 데이터를 초기화하지 않는다. 서버가 허용한 출처 `http://localhost:5173`를 사용한다.

VS Code PowerShell의 frontend 폴더에서:

```powershell
$env:VITE_API_MODE="real"
$env:VITE_API_BASE_URL="http://localhost:8080"
npm.cmd run dev
```

지도가 필요한 목적지 선택은 허용 도메인이 설정된 프론트용 카카오 JavaScript 키가 필요하다.
서버용 REST 키와 JWT_SECRET은 프론트에 넣지 않는다. 기존 프론트 키 설정은 유지한다.

1. 실제 계정으로 로그인한다. 개발자 도구 Network에서 `/ws` 연결과 STOMP CONNECT/CONNECTED를 확인한다.
   토큰이 담긴 프레임/응답 원문을 캡처해 공유하지 않는다.
2. 매칭 요청을 만들고 대기 화면을 연다. `/user/queue/status` 구독과 실제 MESSAGE 수신,
   요청 ID·남은 시간·후보 수가 REST 상세와 일치하는지 확인한다. 매칭/다른 계정 요청을 임의 생성하지 않는다.
3. 네트워크를 끊었다가 복구한다. 단절 안내, 재접속, 구독 복원, 상세 재조회가 되는지 확인한다.
   기존 REST 폴링도 살아 있으므로 화면 숫자 변경만으로 WS 성공을 판정하지 않는다.
4. 이번에 만든 요청을 취소한다. 새 요청을 만들었다면 이전 요청의 알림이 덮어쓰지 않는지 확인한다.
5. `/me`에서 닉네임·학과·학년을 수정하고 새로고침해 확인한다. 선택 필드를 비우면 null로 지워진다.
6. 로그아웃하면 소켓이 닫히고 재접속하지 않는지 확인한다. 만료 테스트는 전용 테스트 토큰/환경에서 수행한다.

실제 결과를 기록하기 전 T1-7 또는 전체 Phase를 완료로 체크하지 않는다.
그룹 API·실제 멤버십이 준비되면 두 테스트 계정으로 그룹 조회/수락/채팅/완료까지 별도 확인한다.

## 담당자 전달용 (자동 전송하지 않음)

- 임승현: `JwtTokenProvider.verify`에서 계약의 필수 exp·양수 subject를 거절하는 경계 검사와 회귀 테스트 확인 요청.
- 서준: 그룹/이력/통계 API 및 StubMatchHistoryAdapter의 실제 멤버십 조회 구현 요청.
- 지도: 송준호의 estimated 구현을 받아 GroupRouteMap/DemoPage 전달과 실선/점선 대역 검사는 완료했다. 실제 카카오 지도의 추정 경로도 환경 준비 후 확인한다.
