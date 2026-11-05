# features/map — 송준호 소유

카카오맵 관련 컴포넌트가 들어간다 (PRD §14.6).

- `useKakaoMap` — SDK 로딩 훅
- `RouteMap` — 합승 경로 Polyline + 탑승·하차 순서 마커. `features/group` 이 쓴다
- `DestinationPicker` — 목적지 검색·핀 확인. `features/request` 가 쓴다

props 계약은 PRD §14.6 에 고정돼 있다. 바꾸려면 `[계약 변경 요청]`.
