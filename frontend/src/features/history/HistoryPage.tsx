import { pickupName } from '@/shared/pickupName';
import { Link, useSearchParams } from 'react-router-dom';
import { Card, Button } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { won, koreanDate } from '@/shared/api/format';
import { useStats, useHistory } from './useHistory';
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const month = params.get('month') ?? '';
  const n = Number(params.get('page'));
  const page = Number.isSafeInteger(n) && n >= 0 ? n : 0;
  function filter(nextMonth: string, nextPage: number) {
    setParams({
      ...(nextMonth ? { month: nextMonth } : {}),
      ...(nextPage ? { page: String(nextPage) } : {}),
    });
  }
  const history = useHistory(page, month);
  const stats = useStats();
  return (
    <section className="page">
      <h1 className="text-2xl font-bold">함께한 이동</h1>
      <Card>
        <p className="text-sm text-slate-600">지금까지 아낀 금액</p>
        {stats.error ? (
          <QueryState error={stats.error} retry={() => void stats.refetch()} />
        ) : stats.data ? (
          <>
            <p className="mt-2 text-3xl font-bold text-saving">{won(stats.data.totalSaving)}</p>
            <p className="mt-2 text-sm text-slate-600">총 {stats.data.totalRides}번의 동승</p>
          </>
        ) : (
          <p className="mt-2 text-sm text-slate-600">
            {stats.isLoading ? '불러오는 중' : '누적 통계를 아직 불러올 수 없어요.'}
          </p>
        )}
      </Card>
      <p className="text-sm text-slate-600">
        동승 채팅은 매칭 확정 후 3시간까지 다시 볼 수 있어요.
      </p>
      <label className="block text-sm font-semibold">
        조회 월
        <input
          aria-label="조회 월"
          type="month"
          className="field mt-2"
          value={month}
          onChange={(e) => {
            filter(e.target.value, 0);
          }}
        />
      </label>
      {month && (
        <Button
          variant="ghost"
          onClick={() => {
            filter('', 0);
          }}
        >
          전체 기간 보기
        </Button>
      )}
      {history.isLoading || history.error ? (
        <QueryState
          loading={history.isLoading}
          error={history.error}
          retry={() => void history.refetch()}
        />
      ) : !history.data?.content.length ? (
        <Card>
          <p className="text-sm text-slate-600">이 기간의 동승 이력이 없어요.</p>
        </Card>
      ) : (
        history.data.content.map((item) => (
          <Card key={item.groupId}>
            <p className="text-xs text-slate-600">
              {koreanDate(item.ridedAt)} · {item.memberCount}명 동승
            </p>
            <h2 className="mt-2 font-bold">
              {pickupName(item.hubName)} → {item.destName}
            </h2>
            <div className="mt-4 flex justify-between gap-3">
              <strong>{won(item.shareAmount)}</strong>
              <span className="text-sm font-semibold text-saving">
                {won(item.savingAmount)} 절약
              </span>
            </div>
            <div className="history-actions">
              <Link
                className="link"
                to={`/groups/${item.groupId}/fare`}
                state={{ fromHistory: true }}
              >
                정산 상세
              </Link>
              <Link
                className="link"
                to={`/groups/${item.groupId}/chat`}
                state={{ fromHistory: true, historySearch: params.toString() }}
              >
                채팅 다시 보기
              </Link>
            </div>
          </Card>
        ))
      )}
      <div className="flex items-center justify-between">
        <Button
          variant="secondary"
          disabled={page === 0 || history.isFetching}
          onClick={() => filter(month, page - 1)}
        >
          이전
        </Button>
        <span className="text-sm">{page + 1}쪽</span>
        <Button
          variant="secondary"
          disabled={!history.data || history.data.last || history.isFetching}
          onClick={() => filter(month, page + 1)}
        >
          다음
        </Button>
      </div>
    </section>
  );
}
