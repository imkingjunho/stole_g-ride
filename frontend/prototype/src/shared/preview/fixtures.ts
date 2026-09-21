// PRD §5.2의 계산 예시를 화면에 옮긴 고정 전시 자료다. API DTO나 정산 엔진이 아니다.
// 100원 올림 후 A 1,500 / B 3,800 / C 9,700원 = 15,000원. B의 보정은 +50원.
export const fareExhibit = {
  total: '15,000',
  solo: '9,800',
  share: '3,800',
  saving: '6,000',
  savingPercent: '61',
  beforeRounding: '3,750',
  adjustment: '+50',
  segments: [
    {
      id: 'S1',
      route: '전남대 후문 → 첫 번째 하차',
      distance: '3.0 km',
      people: 3,
      fare: '4,500',
      mine: '1,500',
      active: true,
    },
    {
      id: 'S2',
      route: '첫 번째 하차 → 내 하차',
      distance: '3.0 km',
      people: 2,
      fare: '4,500',
      mine: '2,250',
      active: true,
    },
    {
      id: 'S3',
      route: '내 하차 → 마지막 하차',
      distance: '4.0 km',
      people: 1,
      fare: '6,000',
      mine: '0',
      active: false,
    },
  ],
} as const;

export const historyExhibit = [
  {
    id: 'sample-1',
    date: '9월 18일 · 17:20',
    destination: '유스퀘어',
    people: 3,
    paid: '3,800',
    saving: '6,000',
    note: '정산 설명 보기',
  },
  {
    id: 'sample-2',
    date: '9월 16일 · 08:40',
    destination: '광주송정역',
    people: 2,
    paid: '6,900',
    saving: '5,500',
    note: '예시 이용 내역',
  },
  {
    id: 'sample-3',
    date: '9월 14일 · 18:10',
    destination: '경신여고',
    people: 2,
    paid: '2,900',
    saving: '2,700',
    note: '예시 이용 내역',
  },
] as const;
