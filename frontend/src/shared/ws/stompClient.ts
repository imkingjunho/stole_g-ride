import { Client, type StompSubscription } from '@stomp/stompjs';
import type { components } from '@/generated/api';
import { isMockMode, apiBaseUrl } from '@/shared/api/config';
import { useAuthStore } from '@/shared/stores/authStore';
import { useConnectionStore } from './connectionStore';
import { addMockChat, listenMock } from '@/shared/mocks/state';
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
  private subscriptions = new Map<
    number,
    { destination: string; handler: MessageHandler; active?: StompSubscription }
  >();
  connect() {
    if (this.client) return;
    useConnectionStore.setState({ status: 'connecting' });
    const client = new Client({
      brokerURL: `${apiBaseUrl.replace(/^http/, 'ws')}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: () => {
        const token = useAuthStore.getState().accessToken;
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        useConnectionStore.setState({ status: 'connected' });
        for (const id of this.subscriptions.keys()) this.attach(id);
      },
      onWebSocketClose: () => {
        useConnectionStore.setState({ status: 'disconnected' });
        for (const entry of this.subscriptions.values()) entry.active = undefined;
      },
      onStompError: () => useConnectionStore.setState({ status: 'disconnected' }),
    });
    this.client = client;
    client.activate();
  }
  private attach(id: number) {
    const entry = this.subscriptions.get(id);
    if (!entry || !this.client?.connected) return;
    entry.active?.unsubscribe();
    entry.active = this.client.subscribe(entry.destination, (frame) => {
      try {
        entry.handler(JSON.parse(frame.body));
      } catch {
        useConnectionStore.setState({ status: 'disconnected' });
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
      const deadline = Date.now() + 600000;
      timer = setInterval(() => {
        const remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
        handler({
          requestId: 1,
          status: remainingSeconds > 0 ? 'WAITING' : 'EXPIRED',
          remainingSeconds,
          candidateCount: 2,
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
