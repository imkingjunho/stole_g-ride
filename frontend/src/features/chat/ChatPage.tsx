import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import type { components } from '@/generated/api';
import { api, unwrap, showError } from '@/shared/api/client';
import { koreaTime } from '@/shared/api/format';
import { useAuthStore } from '@/shared/stores/authStore';
import { stompClient, isChatMessage } from '@/shared/ws/stompClient';
import { useConnectionStore } from '@/shared/ws/connectionStore';
import { Button, Card } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { useGroup } from '@/features/group/useGroup';
import { ReportButton } from '@/features/group/ReportButton';
type Message = components['schemas']['ChatMessage'];
function mergeMessages(a: Message[], b: Message[]) {
  return [...new Map([...a, ...b].map((m) => [m.id, m])).values()].sort(
    (a, b) => koreaTime(a.createdAt).getTime() - koreaTime(b.createdAt).getTime() || a.id - b.id,
  );
}
export function ChatPage() {
  const group = useGroup();
  const id = group.id;
  const userId = useAuthStore((s) => s.user?.id);
  const status = useConnectionStore((s) => s.status);
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');
  const end = useRef<HTMLDivElement>(null);
  const incoming = useRef<Message[]>([]);
  const messages = useQuery({
    queryKey: ['messages', id],
    enabled: Number.isSafeInteger(id) && id > 0,
    queryFn: async () => {
      const r = await api.GET('/api/groups/{groupId}/messages', {
        params: { path: { groupId: id } },
      });
      return mergeMessages(unwrap(r.data, r.error) ?? [], incoming.current);
    },
  });
  useEffect(() => {
    incoming.current = [];
    return stompClient.subscribe(`/topic/chat/${id}`, (value) => {
      if (!isChatMessage(value) || value.groupId !== id) return;
      incoming.current = mergeMessages(incoming.current, [value]);
      qc.setQueryData<Message[]>(['messages', id], (old) => mergeMessages(old ?? [], [value]));
    });
  }, [id, qc]);
  useEffect(() => {
    if (status === 'connected') void qc.invalidateQueries({ queryKey: ['messages', id] });
  }, [status, qc, id]);
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'nearest' });
  }, [messages.data?.length]);
  const closed = group.data?.status !== 'CONFIRMED';
  function send(content: string, type: 'TEXT' | 'QUICK') {
    if (closed || !content.trim() || content.trim().length > 500) return;
    try {
      stompClient.send(`/app/chat/${id}`, { type, content: content.trim() });
      if (type === 'TEXT') setDraft('');
    } catch (e) {
      showError(e);
    }
  }
  if (!group.data)
    return (
      <QueryState
        loading={group.isLoading}
        error={group.error}
        retry={() => void group.refetch()}
      />
    );
  return (
    <section className="page">
      <header>
        <Link className="link px-0" to={`/groups/${id}`}>
          ← 동승 정보
        </Link>
        <h1 className="text-2xl font-bold">동승 채팅</h1>
        <p className="mt-2 text-sm text-slate-600">이 대화는 탑승 완료 후 삭제됩니다.</p>
      </header>
      {status !== 'connected' && (
        <p role="status" className="rounded-control bg-amber-100 p-3 text-sm text-amber-900">
          연결이 끊겼어요. 다시 연결하고 있어요.
        </p>
      )}
      <Card className="space-y-4">
        <div
          role="log"
          aria-label="대화 내역"
          aria-live="polite"
          className="max-h-[45dvh] space-y-4 overflow-y-auto"
        >
          {messages.isLoading || messages.error ? (
            <QueryState
              loading={messages.isLoading}
              error={messages.error}
              retry={() => void messages.refetch()}
            />
          ) : messages.data?.length ? (
            messages.data.map((m) =>
              m.type === 'SYSTEM' ? (
                <p
                  key={m.id}
                  className="rounded-control bg-canvas p-3 text-center text-xs text-slate-600"
                >
                  {m.content}
                </p>
              ) : (
                <article key={m.id} className={m.senderId === userId ? 'ml-6 text-right' : 'mr-6'}>
                  <p className="mb-1 text-xs text-slate-600">
                    {m.senderNickname}
                    {m.senderId === userId ? ' · 나' : ''}
                  </p>
                  <p
                    className={`inline-block max-w-full whitespace-pre-wrap break-words rounded-control p-3 text-left text-sm ${m.senderId === userId ? 'bg-brand text-white' : 'bg-canvas text-ink'}`}
                  >
                    {m.content}
                  </p>
                  {m.content.includes('***') && (
                    <p className="mt-1 text-xs text-slate-600">개인정보가 가려진 메시지</p>
                  )}
                  <time className="mt-1 block text-xs text-slate-600">
                    {koreaTime(m.createdAt).toLocaleTimeString('ko-KR', {
                      timeZone: 'Asia/Seoul',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </time>
                </article>
              ),
            )
          ) : (
            <p className="text-sm text-slate-600">첫 인사를 나눠 보세요.</p>
          )}
          <div ref={end} />
        </div>
      </Card>
      {closed ? (
        <p role="status" className="text-sm text-slate-600">
          현재 그룹 상태에서는 대화를 보낼 수 없어요.
        </p>
      ) : (
        <>
          <div className="flex flex-wrap gap-2">
            {['도착했어요', '5분 늦어요', '출발합니다'].map((text) => (
              <Button
                key={text}
                variant="secondary"
                disabled={status !== 'connected'}
                onClick={() => send(text, 'QUICK')}
              >
                {text}
              </Button>
            ))}
          </div>
          <form
            className="space-y-2"
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              send(draft, 'TEXT');
            }}
          >
            <label htmlFor="message" className="sr-only">
              메시지
            </label>
            <textarea
              id="message"
              className="field resize-y"
              rows={2}
              maxLength={500}
              placeholder="만날 장소를 이야기해 보세요"
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
                  send(draft, 'TEXT');
                }
              }}
            />
            <div className="flex items-center justify-between">
              <p className="text-xs text-slate-600">{draft.length}/500 · Shift+Enter 줄바꿈</p>
              <Button type="submit" disabled={status !== 'connected' || !draft.trim()}>
                보내기
              </Button>
            </div>
          </form>
        </>
      )}
      <ReportButton group={group.data} />
    </section>
  );
}
