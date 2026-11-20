import './chat.css';
import { pickupName } from '@/shared/pickupName';
import { LegalNotice } from '@/shared/ui/LegalNotice';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router-dom';
import type { components } from '@/generated/api';
import { api, unwrap, showError } from '@/shared/api/client';
import { koreaTime } from '@/shared/api/format';
import { useAuthStore } from '@/shared/stores/authStore';
import { stompClient, isChatMessage } from '@/shared/ws/stompClient';
import { useConnectionStore } from '@/shared/ws/connectionStore';
import { QueryState } from '@/shared/ui/QueryState';
import { useGroup } from '@/features/group/useGroup';
import { CompleteRideButton } from '@/features/group/CompleteRideButton';
import { useCountdown } from '@/shared/ui/useCountdown';
import { ReportButton } from '@/features/group/ReportButton';
type Message = components['schemas']['ChatMessage'];
function mergeMessages(a: Message[], b: Message[]) {
  return [...new Map([...a, ...b].map((m) => [m.id, m])).values()].sort(
    (a, b) => koreaTime(a.createdAt).getTime() - koreaTime(b.createdAt).getTime() || a.id - b.id,
  );
}
export function ChatPage() {
  const group = useGroup();
  const { state } = useLocation();
  const historyUrl = {
    pathname: '/history',
    search: typeof state?.historySearch === 'string' ? state.historySearch : '',
  };
  const id = group.id;
  const userId = useAuthStore((s) => s.user?.id);
  const status = useConnectionStore((s) => s.status);
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');
  const end = useRef<HTMLDivElement>(null);
  const incoming = useRef<{ groupId: number; messages: Message[] }>({ groupId: id, messages: [] });
  const seconds = useCountdown(
    group.data?.chatExpiresAt ? koreaTime(group.data.chatExpiresAt).getTime() : 0,
  );
  const canChat =
    !group.error &&
    ['CONFIRMED', 'COMPLETED'].includes(group.data?.status ?? '') &&
    (!group.data?.chatExpiresAt || seconds > 0);
  const messages = useQuery({
    queryKey: ['messages', id],
    enabled: canChat,
    queryFn: async () => {
      const r = await api.GET('/api/groups/{groupId}/messages', {
        params: { path: { groupId: id } },
      });
      return mergeMessages(
        unwrap(r.data, r.error) ?? [],
        incoming.current.groupId === id ? incoming.current.messages : [],
      );
    },
  });
  useEffect(() => {
    if (incoming.current.groupId !== id) incoming.current = { groupId: id, messages: [] };
    if (!canChat) {
      incoming.current.messages = [];
      return;
    }
    return stompClient.subscribe(`/topic/chat/${id}`, (value) => {
      if (!isChatMessage(value) || value.groupId !== id) return;
      if (incoming.current.groupId !== id) return;
      incoming.current.messages = mergeMessages(incoming.current.messages, [value]);
      qc.setQueryData<Message[]>(['messages', id], (old) => mergeMessages(old ?? [], [value]));
    });
  }, [id, qc, canChat]);
  useEffect(() => {
    if (canChat && status === 'connected')
      void qc.invalidateQueries({ queryKey: ['messages', id] });
  }, [status, qc, id, canChat]);
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'nearest' });
  }, [messages.data?.length]);
  const closed = !canChat;
  function send(content: string, type: 'TEXT' | 'QUICK') {
    if (closed || !content.trim() || content.trim().length > 500) return;
    try {
      stompClient.send(`/app/chat/${id}`, { type, content: content.trim() });
      if (type === 'TEXT') setDraft('');
    } catch (e) {
      showError(e);
    }
  }
  if (group.error || !group.data)
    return (
      <QueryState
        loading={group.isLoading}
        error={group.error}
        retry={() => void group.refetch()}
      />
    );
  return (
    <section className="ride-chat">
      <header className="room-head">
        <Link
          className="room-back"
          aria-label={state?.fromHistory ? '이력으로 돌아가기' : '동승 정보로 돌아가기'}
          to={state?.fromHistory ? historyUrl : '/groups/' + id}
        >
          ‹
        </Link>
        <div>
          <h1>
            동승 채팅 <small>{group.data.members.length}명</small>
          </h1>
          <p>{pickupName(group.data.hub.name)}</p>
        </div>
        <details className="room-menu">
          <summary aria-label="채팅 메뉴">···</summary>
          <div>
            <Link to={'/groups/' + id}>동승 정보</Link>
            <Link to={historyUrl}>함께한 이동</Link>
            <ReportButton group={group.data} />
            <LegalNotice />
          </div>
        </details>
      </header>
      <div className="room-log" role="log" aria-label="대화 내역" aria-live="polite">
        {closed ? (
          <p className="room-status">
            {group.data.chatExpiresAt && seconds === 0
              ? '채팅 보관 기간이 끝났어요. 매칭 확정 후 3시간이 지나 대화가 삭제됐어요.'
              : '종료된 채팅방이에요.'}
          </p>
        ) : (
          <>
            {status !== 'connected' && <p className="room-status">연결이 끊겼어요. 다시 연결하고 있어요.</p>}
            {messages.isLoading || messages.error ? (
              <QueryState
                loading={messages.isLoading}
                error={messages.error}
                retry={() => void messages.refetch()}
              />
            ) : messages.data?.length ? (
              messages.data.map((m) => {
                if (m.type === 'SYSTEM')
                  return (
                    <p key={m.id} className="room-status">
                      {m.content}
                    </p>
                  );
                const mine = m.senderId === userId;
                return (
                  <article key={m.id} className={'room-row' + (mine ? ' mine' : '')}>
                    {!mine && (
                      <span aria-hidden="true" className="room-avatar">
                        {m.senderNickname.slice(0, 1)}
                      </span>
                    )}
                    <div className="room-message">
                      {!mine && <strong>{m.senderNickname}</strong>}
                      <div className="room-bubble-line">
                        <p>{m.content}</p>
                        <time dateTime={m.createdAt}>
                          {koreaTime(m.createdAt).toLocaleTimeString('ko-KR', {
                            timeZone: 'Asia/Seoul',
                            hour: 'numeric',
                            minute: '2-digit',
                          })}
                        </time>
                      </div>
                      {m.content.includes('***') && <small>개인정보가 가려진 메시지</small>}
                    </div>
                  </article>
                );
              })
            ) : (
              <p className="room-status">첫 인사를 나눠 보세요.</p>
            )}
          </>
        )}
        <div ref={end} />
      </div>
      <footer className="room-compose">
        <CompleteRideButton key={id} group={group.data} />
        {!closed && (
          <>
            <div className="room-quick">
              {['도착했어요', '5분 늦어요', '출발합니다'].map((t) => (
                <button key={t} disabled={status !== 'connected'} onClick={() => send(t, 'QUICK')}>
                  {t}
                </button>
              ))}
            </div>
            <form
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                send(draft, 'TEXT');
              }}
            >
              <textarea
                aria-label="메시지"
                rows={1}
                maxLength={500}
                placeholder="메시지 입력"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (
                    e.key === 'Enter' &&
                    !e.shiftKey &&
                    !e.nativeEvent.isComposing &&
                    e.nativeEvent.keyCode !== 229
                  ) {
                    e.preventDefault();
                    if (status === 'connected') send(draft, 'TEXT');
                  }
                }}
              />
              <button aria-label="보내기" disabled={status !== 'connected' || !draft.trim()}>
                ↑
              </button>
            </form>
          </>
        )}
        {closed && (
          <Link className="link w-full" to={historyUrl}>
            함께한 이동 보기
          </Link>
        )}
      </footer>
    </section>
  );
}
