# 그룹 실서버 통합 요청 — 2026-11-11

확인 기준: main `358a3bb`. 아래는 코드 읽기 결과이며 실제 Java 서버 호출 결과가 아니다.

## 반영 확인

- `750e1d2`: 오승원의 대기 push 구독·재접속·추정 경로 연결이 main에 있다.
- `358a3bb`: 서준의 매칭·정산 코드가 합쳐졌고 main CI는 통과했다.
- `matching/adapter/MatchHistoryAdapter`는 DB 멤버십 조회를 사용한다. 예전 항상 true 스텁은 교체됐다.
- 그룹 GET·complete는 컨트롤러가 생겼다. 그러나 현재 형태로는 프론트 계약을 충족하지 못한다.

## 요청 1: 그룹 조회/완료 응답·인가 (서준)

대상: `backend/src/main/java/com/gachiga/matching/api/GroupController.java`, `GroupResponse.java`.

- GET/complete가 현재 `ResponseEntity<GroupResponse>`를 직접 반환한다. 계약은 GET의
  `ApiResponse<GroupDetail>`, complete의 `ApiResponse` 성공 응답(data=null)이다.
- 현재 DTO는 `id`, `hubId`를 반환한다. 명세는 `groupId`, `estimated`, hub 객체, route.sections,
  닉네임·isMe·목적지·하차 순서·soloFare·accepted 등 GroupDetail/GroupMember 필드를 요구한다.
- 서버의 `@CurrentUser`와 실제 멤버십으로 조회/완료 권한을 확인하고 isMe를 산출해야 한다.
  현재 두 메서드에는 사용자 인자와 멤버십 확인이 없다. 프론트 검증은 서버 인가의 대체 수단이 아니다.
- 선택 breakdown/roundingAdjustment가 없을 때 UI는 기존 안내를 유지하지만 필수 요금·좌표·닉네임을 임의로 채우지 않는다.

[계약 변경 요청] 서준 / matching/api/GroupController.java·GroupResponse.java / PRD §14.4·api-spec.yaml의 ApiResponse 및 GroupDetail 반환, 현재 사용자 기반 조회·완료 인가 반영 / 그룹 성사·정산·채팅·신고의 실제 화면 연결에 필요

## 요청 2: P1 수락/거절 (서준)

현재 main에는 `/api/groups/{groupId}/accept`, `/reject` 컨트롤러가 없다.
acceptDeadline, 수락 여부, 410 MATCH_EXPIRED, 해체/재계산·재동의 흐름을 명세대로 제공한다.
프론트는 수락 성공의 GroupDetail을 검증/반영하고, 만료 응답 후 최신 상태를 다시 조회한다.

[계약 변경 요청] 서준 / matching 그룹 수락·거절 API / 60초 마감·accept/reject·해체/재계산을 계약 응답과 연결 / T2-1의 실제 서버 통합 완료에 필요

## 요청 3: 계약 이벤트와 완료 후 정리 (서준)

- realtime/MatchNotificationListener는 `contract.event.*`를 AFTER_COMMIT에 수신한다.
- 현재 matching에는 별도 `matching.event.*` 클래스가 있으며, 확인한 MatchingEngine/GroupController/MatchGroup에서는
  계약 이벤트 발행 코드가 없다. 저장 상태 변경만으로 계약 이벤트가 자동 발행되지는 않는다.
- contract.event의 성사·해체·완료 이벤트를 트랜잭션에서 발행해 실제 push 및 채팅 정리 수신 경로를 확인해야 한다.

[계약 변경 요청] 서준 / MatchingEngine·GroupController.complete / contract.event의 확정·해체·완료 이벤트와 요청 상태 연결 / 실제 매칭 알림·그룹 이동·채팅방 정리 검증에 필요

## 요청 4: 이력/통계 (서준)

현재 main에서 GET `/api/history`, `/api/stats/me`, `/api/admin/stats` 컨트롤러는 확인되지 않았다.

[계약 변경 요청] 서준 / stats / 명세의 이력·개인 통계·관리 통계 API 제공 / 실제 완료 후 이력과 누적 절감액 확인에 필요

## 다음 실제 검사

서버 수정 후 전용 계정 2개로 그룹 성사 → 조회 → 수락/거절 → 채팅 → 완료를 확인한다.
비구성원 GET/POST/채팅은 거절되고, 완료 후 메시지가 보이지 않는지도 검사한다.
현재 브라우저 대역 검사를 실서버 인가·매칭 검증이나 Phase 전체 완료로 기록하지 않는다.
외부 메시지는 자동 전송하지 않았다.
