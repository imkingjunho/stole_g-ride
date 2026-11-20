const names: Record<string, string> = {
  '전남대 후문': '전남대 후문 스타벅스',
  '전남대 정문': '전남대 정문 이디야 커피',
  '예대 삼거리': '전대 예대점 CU',
  경신여고: '경신여고 새약손 한방병원',
  유스퀘어: '유스퀘어 택시정류장',
  광주송정역: '광주송정역 택시정류장',
};

export function pickupName(hub: string) {
  return names[hub === '전남대 예대 삼거리' ? '예대 삼거리' : hub] ?? hub;
}

// 구버전 서버가 반환하는 거점 중심점도 지정 승차 지점에 표시한다.
// 현행 서버는 지점명을 직접 반환하므로 해당 응답 좌표를 그대로 사용한다.
const legacyPoints: Record<string, { lat: number; lng: number }> = {
  '전남대 후문': { lat: 35.1772166772749, lng: 126.912456703755 },
  '전남대 정문': { lat: 35.17230611135343, lng: 126.90530361924392 },
  '예대 삼거리': { lat: 35.1807257300084, lng: 126.90421466731 },
  경신여고: { lat: 35.1730500119223, lng: 126.887698176817 },
  유스퀘어: { lat: 35.1610463, lng: 126.8785098 },
  광주송정역: { lat: 35.1363353244415, lng: 126.790300414821 },
};

export function resolvePickup<T extends { name: string; lat: number; lng: number }>(hub: T): T {
  const point = legacyPoints[hub.name === '전남대 예대 삼거리' ? '예대 삼거리' : hub.name];
  return point ? { ...hub, ...point } : hub;
}
