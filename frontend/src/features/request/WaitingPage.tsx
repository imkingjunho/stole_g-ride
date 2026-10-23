import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api, isMockMode, showError, unwrap } from '@/shared/api/client';
import { Button, Card } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { useCountdown } from '@/shared/ui/useCountdown';

export function WaitingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const requestQuery = useQuery({
    queryKey: ['myRequest'],
    retry: false,
    refetchInterval: isMockMode ? false : 5000,
    queryFn: async () => {
      const response = await api.GET('/api/requests/me');
      return unwrap(response.data, response.error);
    },
  });
  const request = requestQuery.data;
  const deadline = request ? requestQuery.dataUpdatedAt + request.remainingSeconds * 1000 : 0;
  const remaining = useCountdown(deadline);

  useEffect(() => {
    if (request?.groupId && ['MATCHED', 'CONFIRMED'].includes(request.status)) {
      navigate(`/groups/${request.groupId}`, { replace: true });
    }
  }, [navigate, request?.groupId, request?.status]);

  const cancelRequest = useMutation({
    mutationFn: async (requestId: number) => {
      const response = await api.DELETE('/api/requests/{requestId}', {
        params: { path: { requestId } },
      });
      return unwrap(response.data, response.error);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['myRequest'] });
      navigate('/request', { replace: true });
    },
    onError: showError,
  });

  if (requestQuery.isLoading || requestQuery.error) {
    return (
      <section className="page">
        <QueryState
          loading={requestQuery.isLoading}
          error={requestQuery.error}
          retry={() => void requestQuery.refetch()}
        />
      </section>
    );
  }
  if (!request) {
    return (
      <section className="page">
        <Card>
          <h1 className="text-title font-bold">진행 중인 요청이 없어요</h1>
          <Link to="/request" className="link mt-3">
            매칭 요청하기
          </Link>
        </Card>
      </section>
    );
  }

  const expired = request.status === 'EXPIRED' || remaining === 0;

  return (
    <section className="page">
      <header>
        <p className="text-sm font-semibold text-brand">매칭 요청</p>
        <h1 className="mt-2 text-title font-bold">
          {expired ? '대기 시간이 끝났어요' : '함께 갈 사람을 찾고 있어요'}
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          {request.hub.name} → {request.destName}
        </p>
      </header>

      <Card className="space-y-5 text-center">
        {expired ? (
          <p className="text-sm text-slate-600">
            이번 요청은 만료됐어요. 조건을 바꿔 다시 요청할 수 있어요.
          </p>
        ) : (
          <>
            <div>
              <p className="text-sm text-slate-600">남은 대기시간</p>
              <p className="mt-2 text-4xl font-bold tabular-nums text-brand" aria-live="polite">
                {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}
              </p>
            </div>
            <p className="text-sm text-slate-600">
              현재 함께 기다리는 사람 <strong className="text-ink">{request.candidateCount}명</strong>
            </p>
            {request.estimated && (
              <p className="text-xs text-slate-600">혼자 이동 요금은 추정치예요.</p>
            )}
          </>
        )}
      </Card>

      {expired ? (
        <Button fullWidth onClick={() => navigate('/request', { replace: true })}>
          다시 매칭 요청하기
        </Button>
      ) : (
        <Button
          fullWidth
          variant="secondary"
          loading={cancelRequest.isPending}
          disabled={request.status !== 'WAITING'}
          onClick={() => cancelRequest.mutate(request.requestId)}
        >
          요청 취소
        </Button>
      )}
    </section>
  );
}
