import type { components } from '@/generated/api';
import { mockGroup, mockHistory, mockMessages, mockUser, mockRideRequest, mockHubs } from './data';
type S = components['schemas'];
export const mockState = {
  group: structuredClone(mockGroup),
  history: structuredClone(mockHistory.content),
  messages: structuredClone(mockMessages),
  user: structuredClone(mockUser),
  codeFailures: 0,
  codeExpires: 0,
  lockedUntil: 0,
  signupEmail: '',
  expiredOnce: false,
  request: null as S['RideRequestDetail'] | null,
  nextRequestId: 1,
  failures: new Map<string, number>(),
};

/** 목 요청도 사용자가 선택한 값과 서버 만료 시각을 유지한다. 실제 매칭 알고리즘은 흉내 내지 않는다. */
export function readMockRequest() {
  const request = mockState.request;
  if (!request) return null;
  request.remainingSeconds = Math.max(
    0,
    Math.ceil((new Date(request.expiresAt).getTime() - Date.now()) / 1000),
  );
  if (request.status === 'WAITING' && request.remainingSeconds === 0) request.status = 'EXPIRED';
  return request;
}
export function createMockRequest(body: S['CreateRideRequest']) {
  const hub = mockHubs.find((h) => h.id === body.hubId);
  if (!hub) throw new Error('출발 거점을 확인해 주세요.');
  mockState.request = {
    ...structuredClone(mockRideRequest),
    ...body,
    hub,
    requestId: mockState.nextRequestId++,
    userId: mockState.user.id,
    status: 'WAITING',
    groupId: null,
    estimated: true,
    remainingSeconds: body.maxWaitMin * 60,
    expiresAt: new Date(Date.now() + body.maxWaitMin * 60000).toISOString(),
    createdAt: new Date().toISOString(),
  };
  return mockState.request;
}
const listeners = new Map<string, Set<(value: unknown) => void>>();
export function listenMock(destination: string, handler: (value: unknown) => void) {
  let set = listeners.get(destination);
  if (!set) {
    set = new Set();
    listeners.set(destination, set);
  }
  set.add(handler);
  return () => {
    set.delete(handler);
    if (!set.size) listeners.delete(destination);
  };
}
export function emitMock(destination: string, value: unknown) {
  listeners.get(destination)?.forEach((handler) => handler(value));
}
export function notifyMatch(type: S['MatchNotification']['type'], message: string) {
  const request = readMockRequest();
  if (request) {
    if (type === 'COMPLETED') mockState.request = null;
    else if (type === 'DISSOLVED') {
      request.status = request.remainingSeconds > 0 ? 'WAITING' : 'EXPIRED';
      request.groupId = null;
    } else {
      request.status = type === 'CONFIRMED' ? 'CONFIRMED' : 'MATCHED';
      request.groupId = mockState.group.groupId;
    }
  }
  emitMock('/user/queue/match', {
    groupId: mockState.group.groupId,
    type,
    message,
  } satisfies S['MatchNotification']);
}
export function resetGroup(status: S['GroupStatus'] = 'CONFIRMED') {
  mockState.group = structuredClone(mockGroup);
  mockState.group.status = status;
  mockState.group.acceptDeadline =
    status === 'PENDING' ? new Date(Date.now() + 60000).toISOString() : null;
  mockState.group.members.forEach((m) => {
    m.accepted = status !== 'PENDING';
  });
  mockState.messages = structuredClone(mockMessages);
}
export function addMockChat(body: S['ChatSendRequest']) {
  const content = body.content.trim();
  if (!content || content.length > 500) throw new Error('메시지는 1~500자로 입력해 주세요.');
  if (mockState.group.status !== 'CONFIRMED') throw new Error('대화가 종료됐어요.');
  const message: S['ChatMessage'] = {
    id: Math.max(0, ...mockState.messages.map((m) => m.id)) + 1,
    groupId: mockState.group.groupId,
    senderId: mockState.user.id,
    senderNickname: mockState.user.nickname,
    type: body.type,
    content: content
      .replace(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g, '***')
      .replace(/01[016789][ -]?\d{3,4}[ -]?\d{4}/g, '***'),
    createdAt: new Date().toISOString(),
  };
  mockState.messages.push(message);
  emitMock(`/topic/chat/${message.groupId}`, message);
}
