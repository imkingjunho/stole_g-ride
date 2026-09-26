import type { components } from '@/generated/api';
import { mockGroup, mockHistory, mockMessages, mockUser } from './data';
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
};
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
