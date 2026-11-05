import { Client, type StompSubscription } from '@stomp/stompjs';
import type { components } from '@/generated/api';
import { isMockMode, apiBaseUrl } from '@/shared/api/config';
import { api, unwrap } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import { useConnectionStore } from './connectionStore';
import { addMockChat, listenMock, readMockRequest } from '@/shared/mocks/state';
type MessageHandler = (body: unknown) => void;
export type QueueStatusPayload = components['schemas']['QueueStatus'];
export type MatchNotification = components['schemas']['MatchNotification'];
export type ChatMessagePayload = components['schemas']['ChatMessage'];
export type ChatSendBody = components['schemas']['ChatSendRequest'];
export interface StompClient {
  connect(): void;
  disconnect(): void;
  subscribe(destination: string, handler: MessageHandler): () => void;
  send(destination: string, body: ChatSendBody): void;
}
class RealStompClient implements StompClient {
  private client: Client | null = null;
  private nextId = 0;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;
  private subscriptions = new Map<
    number,
    { destination: string; handler: MessageHandler; active?: StompSubscription }
  >();
  connect() {
    if (this.client) return;
    useConnectionStore.setState({ status: 'connecting' });
    const sessionVersion = useAuthStore.getState().sessionVersion;
    const isCurrent = () =>
      this.client === client && useAuthStore.getState().sessionVersion === sessionVersion;
    const client = new Client({
      brokerURL: `${apiBaseUrl.replace(/^http/, 'ws')}/ws`,
      reconnectDelay: 3000,
      connectionTimeout: 10000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        if (!isCurrent()) {
          await client.deactivate();
          return;
        }
        useConnectionStore.setState({ status: 'connecting' });
        try {
          // 브라우저를 다시 열거나 재접속할 때 REST의 401 갱신 경로를 공유한다.
          // CONNECT 오류 문구를 파싱하거나 Refresh를 별도로 회전하지 않는다.
          const response = await api.GET('/api/users/me', { signal: AbortSignal.timeout(10000) });
          const user = unwrap(response.data, response.error);
          const session = useAuthStore.getState();
          if (!isCurrent() || !session.accessToken || user?.id !== session.user?.id) {
            await client.deactivate();
            return;
          }
          client.connectHeaders = { Authorization: `Bearer ${session.accessToken}` };
        } catch {
          // beforeConnect 예외는 라이브러리의 소켓 재접속 루프에 들어가지 않는다.
          // 일시적인 HTTP 장애는 별도로 재시도하고, 로그아웃된 세션은 중단한다.
          await client.deactivate();
          if (!isCurrent()) return;
          useConnectionStore.setState({ status: 'disconnected' });
          this.retryTimer = setTimeout(() => {
            this.retryTimer = undefined;
            if (isCurrent()) client.activate();
          }, 3000);
        }
      },
      onConnect: () => {
        if (!isCurrent()) return;
        useConnectionStore.setState({ status: 'connected' });
        for (const id of this.subscriptions.keys()) this.attach(id);
      },
      onWebSocketClose: () => {
        if (!isCurrent()) return;
        useConnectionStore.setState({ status: 'disconnected' });
        for (const entry of this.subscriptions.values()) entry.active = undefined;
      },
      onStompError: () => {
        if (isCurrent()) useConnectionStore.setState({ status: 'disconnected' });
      },
    });
    this.client = client;
    client.activate();
  }
  private attach(id: number) {
    const entry = this.subscriptions.get(id);
    if (!entry || !this.client?.connected) return;
    const client = this.client;
    entry.active?.unsubscribe();
    entry.active = client.subscribe(entry.destination, (frame) => {
      if (this.client !== client) return;
      try {
        entry.handler(JSON.parse(frame.body));
      } catch {
        // 잘못된 메시지 하나가 정상 소켓의 연결 상태를 바꾸지 않게 한다.
      }
    });
  }
  subscribe(destination: string, handler: MessageHandler) {
    const id = ++this.nextId;
    this.subscriptions.set(id, { destination, handler });
    this.attach(id);
    return () => {
      this.subscriptions.get(id)?.active?.unsubscribe();
      this.subscriptions.delete(id);
    };
  }
  send(destination: string, body: ChatSendBody) {
    if (!this.client?.connected) throw new Error('연결이 끊겼어요. 재연결 후 전송해 주세요.');
    this.client.publish({ destination, body: JSON.stringify(body) });
  }
  disconnect() {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = undefined;
    const client = this.client;
    this.client = null;
    this.subscriptions.clear();
    useConnectionStore.setState({ status: 'disconnected' });
    void client?.deactivate();
  }
}
class MockStompClient implements StompClient {
  private cleanups = new Set<() => void>();
  connect() {
    useConnectionStore.setState({ status: 'connected' });
  }
  disconnect() {
    this.cleanups.forEach((fn) => fn());
    this.cleanups.clear();
    useConnectionStore.setState({ status: 'disconnected' });
  }
  subscribe(destination: string, handler: MessageHandler) {
    const removeListener = listenMock(destination, handler);
    let timer: ReturnType<typeof setInterval> | undefined;
    if (destination === '/user/queue/status') {
      timer = setInterval(() => {
        const request = readMockRequest();
        if (!request) return;
        handler({
          requestId: request.requestId,
          status: request.status,
          remainingSeconds: request.remainingSeconds,
          candidateCount: request.candidateCount,
        } satisfies QueueStatusPayload);
      }, 5000);
    }
    const off = () => {
      removeListener();
      if (timer) clearInterval(timer);
    };
    this.cleanups.add(off);
    return () => {
      off();
      this.cleanups.delete(off);
    };
  }
  send(destination: string, body: ChatSendBody) {
    if (useConnectionStore.getState().status !== 'connected') throw new Error('연결이 끊겼어요.');
    if (!/^\/app\/chat\/\d+$/.test(destination)) throw new Error('잘못된 채팅 주소예요.');
    addMockChat(body);
  }
}
export const stompClient: StompClient = isMockMode ? new MockStompClient() : new RealStompClient();
export function isMatchNotification(value: unknown): value is MatchNotification {
  return (
    typeof value === 'object' &&
    value !== null &&
    'groupId' in value &&
    typeof value.groupId === 'number' &&
    'message' in value &&
    typeof value.message === 'string' &&
    'type' in value &&
    typeof value.type === 'string' &&
    ['PROPOSED', 'CONFIRMED', 'RECALCULATED', 'DISSOLVED', 'COMPLETED'].includes(value.type)
  );
}
export function isChatMessage(value: unknown): value is ChatMessagePayload {
  return (
    typeof value === 'object' &&
    value !== null &&
    'id' in value &&
    typeof value.id === 'number' &&
    'groupId' in value &&
    typeof value.groupId === 'number' &&
    'content' in value &&
    typeof value.content === 'string' &&
    'senderId' in value &&
    typeof value.senderId === 'number' &&
    'senderNickname' in value &&
    typeof value.senderNickname === 'string' &&
    'createdAt' in value &&
    typeof value.createdAt === 'string' &&
    'type' in value &&
    ['TEXT', 'SYSTEM', 'QUICK'].includes(String(value.type))
  );
}

export function isQueueStatus(value: unknown): value is QueueStatusPayload {
  if (typeof value !== 'object' || value === null) return false;
  return (
    'requestId' in value &&
    typeof value.requestId === 'number' &&
    Number.isSafeInteger(value.requestId) &&
    value.requestId > 0 &&
    'status' in value &&
    typeof value.status === 'string' &&
    ['WAITING', 'MATCHED', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'COMPLETED'].includes(
      value.status,
    ) &&
    'remainingSeconds' in value &&
    typeof value.remainingSeconds === 'number' &&
    Number.isInteger(value.remainingSeconds) &&
    value.remainingSeconds >= 0 &&
    'candidateCount' in value &&
    typeof value.candidateCount === 'number' &&
    Number.isInteger(value.candidateCount) &&
    value.candidateCount >= 0
  );
}
