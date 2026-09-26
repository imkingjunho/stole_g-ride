import type { components } from '@/generated/api';

type Schemas = components['schemas'];

/**
 * MSW 가 돌려줄 가짜 데이터. PRD §3.2 시나리오와 §14.4 예시를 그대로 옮겼다.
 * "그럴싸한" 값이어야 화면을 만들면서 어색한 곳을 발견할 수 있다.
 */

export const mockHubs: Schemas['Hub'][] = [
  { id: 1, name: '전남대 정문', lat: 35.1724, lng: 126.9048, type: 'CAMPUS' },
  { id: 2, name: '전남대 후문', lat: 35.1763, lng: 126.9123, type: 'CAMPUS' },
  { id: 3, name: '전남대 예대 삼거리', lat: 35.1784, lng: 126.9042, type: 'CAMPUS' },
  { id: 4, name: '경신여고', lat: 35.1752, lng: 126.8923, type: 'SCHOOL' },
  { id: 5, name: '유스퀘어', lat: 35.1604, lng: 126.8794, type: 'TERMINAL' },
  { id: 6, name: '광주송정역', lat: 35.1378, lng: 126.7902, type: 'STATION' },
];

export const mockUser: Schemas['UserProfile'] = {
  id: 1,
  email: 'hong@jnu.ac.kr',
  nickname: '후문호랑이',
  gender: 'M',
  department: '컴퓨터정보통신공학과',
  grade: 3,
  status: 'ACTIVE',
  suspendedUntil: null,
};

export const mockPlaces: Schemas['Place'][] = [
  {
    name: '광주송정역',
    address: '광주 광산구 상무대로 201',
    lat: 35.1378,
    lng: 126.7902,
    categoryName: '교통,수송 > 기차,철도 > 기차역',
  },
  {
    name: '유스퀘어',
    address: '광주 서구 무진대로 904',
    lat: 35.1604,
    lng: 126.8794,
    categoryName: '교통,수송 > 버스,고속버스',
  },
];

export const mockRideRequest: Schemas['RideRequestDetail'] = {
  requestId: 1,
  userId: 1,
  hub: mockHubs[1],
  destName: '광주송정역',
  destLat: 35.1378,
  destLng: 126.7902,
  departAt: '2026-09-26T08:30:00+09:00',
  expiresAt: '2026-09-26T08:40:00+09:00',
  maxWaitMin: 10,
  sameGenderOnly: true,
  maxDetourRatio: 0.2,
  soloDistance: 10591,
  soloFare: 12400,
  status: 'WAITING',
  remainingSeconds: 540,
  candidateCount: 2,
  estimated: true,
  groupId: null,
  createdAt: '2026-09-26T08:30:00+09:00',
};

/** PRD §5.2 계산 예시 그대로 — 3인, 총 15,000원, 구간 3개 */
export const mockGroup: Schemas['GroupDetail'] = {
  groupId: 17,
  status: 'CONFIRMED',
  estimated: false,
  hub: mockHubs[1],
  totalFare: 15000,
  totalDistance: 10000,
  totalDuration: 1320,
  route: {
    sections: [
      {
        distance: 3000,
        duration: 400,
        path: [
          { lat: 35.1763, lng: 126.9123 },
          { lat: 35.1752, lng: 126.8923 },
        ],
      },
      {
        distance: 3000,
        duration: 420,
        path: [
          { lat: 35.1752, lng: 126.8923 },
          { lat: 35.1604, lng: 126.8794 },
        ],
      },
      {
        distance: 4000,
        duration: 500,
        path: [
          { lat: 35.1604, lng: 126.8794 },
          { lat: 35.1378, lng: 126.7902 },
        ],
      },
    ],
  },
  members: [
    {
      userId: 2,
      nickname: '용봉동다람쥐',
      isMe: false,
      boardingOrder: 1,
      dropoffOrder: 1,
      destName: '경신여고',
      shareAmount: 1500,
      soloFare: 5600,
      savingAmount: 4100,
      detourRatio: 0.02,
      accepted: true,
    },
    {
      userId: 3,
      nickname: '정문부엉이',
      isMe: false,
      boardingOrder: 1,
      dropoffOrder: 2,
      destName: '유스퀘어',
      shareAmount: 3750,
      soloFare: 8200,
      savingAmount: 4450,
      detourRatio: 0.05,
      accepted: true,
    },
    {
      userId: 1,
      nickname: '후문호랑이',
      isMe: true,
      boardingOrder: 1,
      dropoffOrder: 3,
      destName: '광주송정역',
      shareAmount: 9700,
      soloFare: 12400,
      savingAmount: 2700,
      detourRatio: 0.09,
      accepted: true,
    },
  ],
  acceptDeadline: null,
  createdAt: '2026-09-26T08:31:00+09:00',
};

// 정산 명세 §5.2: 각 사용자 구간 몫 + 보정 = 최종 금액, 합계 15,000원.
mockGroup.members.forEach((member, index) => {
  const destinations = [mockHubs[3], mockHubs[4], mockHubs[5]];
  member.destLat = destinations[index].lat;
  member.destLng = destinations[index].lng;
  member.breakdown = [
    { sectionIndex: 0, sectionFare: 4500, onboardCount: 3, share: 1500 },
    ...(index >= 1 ? [{ sectionIndex: 1, sectionFare: 4500, onboardCount: 2, share: 2250 }] : []),
    ...(index >= 2 ? [{ sectionIndex: 2, sectionFare: 6000, onboardCount: 1, share: 6000 }] : []),
  ];
  member.roundingAdjustment = [0, 50, -50][index];
  member.shareAmount = [1500, 3800, 9700][index];
  member.savingAmount = member.soloFare - member.shareAmount;
});

export const mockMessages: Schemas['ChatMessage'][] = [
  {
    id: 1,
    groupId: 17,
    senderId: 0,
    senderNickname: '시스템',
    type: 'SYSTEM',
    content: '매칭이 성사되었습니다. 전남대 후문에서 만나세요.',
    createdAt: '2026-09-26T08:31:00+09:00',
  },
  {
    id: 2,
    groupId: 17,
    senderId: 2,
    senderNickname: '용봉동다람쥐',
    type: 'TEXT',
    content: '후문 앞 편의점에서 만나요',
    createdAt: '2026-09-26T08:31:30+09:00',
  },
];

export const mockHistory: Schemas['HistoryPage'] = {
  content: [
    {
      groupId: 17,
      ridedAt: '2026-09-26T08:55:00+09:00',
      hubName: '전남대 후문',
      destName: '광주송정역',
      memberCount: 3,
      shareAmount: 9700,
      soloFare: 12400,
      savingAmount: 2700,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  last: true,
};

export const mockStats: Schemas['MyStats'] = {
  totalRides: 12,
  totalSaving: 48200,
  averageSavingRate: 0.55,
  mostUsedHubName: '전남대 후문',
};
