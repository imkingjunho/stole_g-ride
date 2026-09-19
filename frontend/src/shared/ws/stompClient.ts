import { Client, type IMessage } from '@stomp/stompjs';
import { isMockMode } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';

type MessageHandler = (body: unknown) => void;

/**
 * STOMP over WebSocket 래퍼 (PRD §14.5).
 *
 * 구독 주소
 * - `/user/queue/status` 내 대기 상태 (5초마다 + 변경 즉시)
 * - `/user/queue/match` 매칭 알림
 * - `/topic/chat/{groupId}` 채팅
 *
 * 발신 주소
 * - `/app/chat/{groupId}`
 *
 * `VITE_API_MODE=mock` 이면 실제로 연결하지 않고 {@link MockStompClient} 가 대신 동작한다.
 * 화면 코드는 두 경우를 구분하지 않아도 된다.
 *
 * Phase 0 에는 서버가 아무것도 보내지 않는다. 연결·구독만 된다.
 * 실제 push 는 임승현이 Phase 1 에서 붙인다.
 */
export interface StompClient {
  connect(): void;
  disconnect(): void;
  subscribe(destination: string, handler: MessageHandler): () => void;
  send(destination: string, body: unknown): void;
}

class RealStompClient implements StompClient {
  private client: Client | null = null;
  private readonly pending = new Map<string, MessageHandler>();

  connect(): void {
    if (this.client) {
      return;
    }
    const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
    const client = new Client({
      brokerURL: `${base.replace(/^http/, 'ws')}/ws`,
      // CONNECT 프레임에 토큰을 싣는다 (PRD §14.5). Phase 0 서버는 검사하지 않는다
      connectHeaders: tokenHeader(),
      reconnectDelay: 3_000,
      onConnect: () => {
        for (const [destination, handler] of this.pending) {
          this.doSubscribe(destination, handler);
        }
      },
    });
    client.activate();
    this.client = client;
  }

  disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this.pending.clear();
  }

  subscribe(destination: string, handler: MessageHandler): () => void {
    this.pending.set(destination, handler);
    if (this.client?.connected) {
      return this.doSubscribe(destination, handler);
    }
    // 아직 연결 전이면 onConnect 에서 이어 구독한다
    return () => this.pending.delete(destination);
  }

  send(destination: string, body: unknown): void {
    this.client?.publish({ destination, body: JSON.stringify(body) });
  }

  private doSubscribe(destination: string, handler: MessageHandler): () => void {
    const subscription = this.client?.subscribe(destination, (message: IMessage) => {
      handler(parseBody(message.body));
    });
    return () => {
      subscription?.unsubscribe();
      this.pending.delete(destination);
    };
  }
}

/**
 * 목 모드용. 실제 연결 없이 타이머로 그럴싸한 메시지를 흘려 준다.
 * 대기 화면·채팅 화면을 백엔드 없이 만들 수 있게 하는 것이 목적이다.
 */
class MockStompClient implements StompClient {
  private readonly timers = new Set<ReturnType<typeof setInterval>>();

  connect(): void {
    // 연결할 곳이 없다
  }

  disconnect(): void {
    for (const timer of this.timers) {
      clearInterval(timer);
    }
    this.timers.clear();
  }

  subscribe(destination: string, handler: MessageHandler): () => void {
    if (destination !== '/user/queue/status') {
      // 그 밖의 주소는 조용히 둔다. 필요해지면 여기에 시나리오를 추가한다
      return () => {};
    }
    // 대기 화면이 카운트다운을 그릴 수 있도록 남은 시간을 줄여 가며 보낸다
    let remainingSeconds = 600;
    const timer = setInterval(() => {
      remainingSeconds = Math.max(0, remainingSeconds - 5);
      handler({
        requestId: 1,
        status: 'WAITING',
        remainingSeconds,
        candidateCount: 2,
      });
    }, 5_000);
    this.timers.add(timer);
    return () => {
      clearInterval(timer);
      this.timers.delete(timer);
    };
  }

  send(destination: string, body: unknown): void {
    console.info('[mock stomp] send', destination, body);
  }
}

function tokenHeader(): Record<string, string> {
  const token = useAuthStore.getState().accessToken;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function parseBody(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

/** 화면에서는 이것만 쓰면 된다. 모드에 따라 알아서 갈린다. */
export const stompClient: StompClient = isMockMode ? new MockStompClient() : new RealStompClient();
