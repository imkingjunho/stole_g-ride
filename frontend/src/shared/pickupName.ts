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
