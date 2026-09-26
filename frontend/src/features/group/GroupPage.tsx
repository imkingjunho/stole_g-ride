import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api, unwrap, showError } from '@/shared/api/client';
import { won, koreaTime } from '@/shared/api/format';
import { Button, Badge, Card, Modal } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { useCountdown } from '@/shared/ui/useCountdown';
import { isMatchNotification, stompClient } from '@/shared/ws/stompClient';
import { useGroup } from './useGroup';
import { ReportButton } from './ReportButton';
export function GroupPage() {
  const query = useGroup();
  const group = query.data;
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [completeOpen, setCompleteOpen] = useState(false);
  const [previousFare, setPreviousFare] = useState<number | null>(null);
  const fareRef = useRef<number | null>(null);
  const me = group?.members.find((m) => m.isMe);
  fareRef.current = me?.shareAmount ?? null;
  const remaining = useCountdown(
    group?.acceptDeadline ? koreaTime(group.acceptDeadline).getTime() : 0,
  );
  useEffect(
    () =>
      stompClient.subscribe('/user/queue/match', (value) => {
        if (
          isMatchNotification(value) &&
          value.groupId === query.id &&
          value.type === 'RECALCULATED'
        ) {
          setPreviousFare(fareRef.current);
          void queryClient.invalidateQueries({ queryKey: ['group', query.id] });
        }
      }),
    [query.id, queryClient],
  );
  useEffect(() => {
    if (group?.status === 'PENDING' && remaining === 0)
      void queryClient.invalidateQueries({ queryKey: ['group', query.id] });
  }, [group?.status, remaining, query.id, queryClient]);
  const action = useMutation({
    mutationFn: async (kind: 'accept' | 'reject' | 'complete') => {
      const path =
        kind === 'accept'
          ? '/api/groups/{groupId}/accept'
          : kind === 'reject'
            ? '/api/groups/{groupId}/reject'
            : '/api/groups/{groupId}/complete';
      const r = await api.POST(path, { params: { path: { groupId: query.id } } });
      return unwrap<unknown>(r.data, r.error);
    },
    onSuccess: (_, kind) => {
      void queryClient.invalidateQueries({ queryKey: ['group', query.id] });
      if (kind === 'accept') setPreviousFare(null);
      if (kind === 'reject') navigate('/waiting', { replace: true });
      if (kind === 'complete') {
        setCompleteOpen(false);
        void queryClient.invalidateQueries({ queryKey: ['history'] });
        void queryClient.invalidateQueries({ queryKey: ['stats'] });
        navigate('/history');
      }
    },
    onError: showError,
  });
  if (!group)
    return (
      <QueryState
        loading={query.isLoading}
        error={query.error}
        retry={() => void query.refetch()}
        empty="동승 정보를 찾을 수 없어요."
      />
    );
  if (group.status === 'DISSOLVED')
    return (
      <section className="page">
        <h1 className="text-title font-bold">동승 그룹이 해체됐어요</h1>
        <Link className="link" to="/waiting">
          대기 상태 확인
        </Link>
      </section>
    );
  return (
    <section className="page">
      <header>
        <p className="text-sm text-brand">함께 가는 즐거움</p>
        <h1 className="mt-2 text-2xl font-bold">
          {group.status === 'PENDING'
            ? '동승 제안이 도착했어요'
            : group.status === 'COMPLETED'
              ? '함께한 이동이 끝났어요'
              : '동승이 성사됐어요!'}
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          {group.hub.name}에서 {group.members.length}명이 함께 출발해요.
        </p>
      </header>
      <Card>
        {group.estimated && <Badge variant="estimated" />}
        {me ? (
          <>
            <p className="mt-3 text-sm text-slate-600">혼자 {won(me.soloFare)}</p>
            <p className="mt-1 text-3xl font-bold">함께 {won(me.shareAmount)}</p>
            <p className="mt-3 font-semibold text-saving">
              {won(me.savingAmount)} 절약{' '}
              {me.soloFare > 0 && `(${Math.round((me.savingAmount / me.soloFare) * 100)}%)`}
            </p>
            <Link className="link mt-2 px-0" to={`/groups/${group.groupId}/fare`}>
              내 요금은 어떻게 계산됐나요? →
            </Link>
          </>
        ) : (
          <p>내 정산 정보를 확인할 수 없어요.</p>
        )}
      </Card>
      {previousFare !== null && me && (
        <Card>
          <h2 className="font-bold">경로와 분담액이 변경됐어요</h2>
          <p className="mt-2">
            {won(previousFare)} → {won(me.shareAmount)}
          </p>
          <p className="mt-2 text-sm text-slate-600">새 금액을 확인하고 다시 수락해 주세요.</p>
        </Card>
      )}
      {group.status === 'PENDING' && (
        <Card className="space-y-3">
          <h2 className="font-bold">
            {remaining > 0 ? `${remaining}초 안에 응답해 주세요` : '응답 시간이 지났어요'}
          </h2>
          {me?.accepted ? (
            <p>수락했어요. 다른 동승자의 응답을 기다리고 있어요.</p>
          ) : (
            <div className="flex gap-2">
              <Button
                fullWidth
                loading={action.isPending}
                disabled={remaining === 0}
                onClick={() => action.mutate('accept')}
              >
                수락
              </Button>
              <Button
                fullWidth
                variant="secondary"
                disabled={action.isPending || remaining === 0}
                onClick={() => action.mutate('reject')}
              >
                거절
              </Button>
            </div>
          )}
        </Card>
      )}
      <Card>
        <h2 className="font-bold">함께 가는 경로</h2>
        <div
          className="my-4 flex h-48 items-center justify-center rounded-control bg-canvas text-sm text-slate-600"
          aria-label="지도 준비 영역"
        >
          경로 지도 준비 중
        </div>
        <p className="text-sm text-slate-600">
          약 {Math.ceil(group.totalDuration / 60)}분 · {(group.totalDistance / 1000).toFixed(1)}km
        </p>
      </Card>
      <Card>
        <h2 className="font-bold">탑승 · 하차 순서</h2>
        <p className="my-3 text-sm">탑승 1 · {group.hub.name}에서 함께 탑승</p>
        <ol className="space-y-4">
          {[...group.members]
            .sort((a, b) => a.dropoffOrder - b.dropoffOrder)
            .map((m) => (
              <li key={m.userId} className="flex items-center gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-soft font-bold text-brand">
                  {m.dropoffOrder}
                </span>
                <div className="min-w-0">
                  <p className="break-words font-semibold">{m.destName}</p>
                  <p className="text-sm text-slate-600">
                    {m.nickname} {m.isMe && <Badge>나</Badge>}
                  </p>
                </div>
              </li>
            ))}
        </ol>
      </Card>
      {group.status === 'CONFIRMED' && (
        <div className="space-y-3">
          <Link className="link w-full bg-brand text-white" to={`/groups/${group.groupId}/chat`}>
            동승자와 채팅하기
          </Link>
          <Button fullWidth variant="secondary" onClick={() => setCompleteOpen(true)}>
            탑승 완료
          </Button>
        </div>
      )}
      <ReportButton group={group} />
      <Modal
        open={completeOpen}
        onClose={() => setCompleteOpen(false)}
        title="탑승을 완료할까요?"
        description="완료 후에는 대화가 종료되고 채팅 내역이 삭제됩니다."
      >
        <Button fullWidth loading={action.isPending} onClick={() => action.mutate('complete')}>
          탑승 완료 확인
        </Button>
      </Modal>
    </section>
  );
}
