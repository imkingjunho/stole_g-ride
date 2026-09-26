import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, Button } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { won, koreanDate } from '@/shared/api/format';
import { useStats, useHistory } from './useHistory';
export function HistoryPage() {
  const [month, setMonth] = useState('');
  const [page, setPage] = useState(0);
  const history = useHistory(page, month);
  const stats = useStats();
  return (
    <section className="page">
      <h1 className="text-2xl font-bold">함께한 이동</h1>
      <Card>
        <p className="text-sm text-slate-600">지금까지 아낀 금액</p>
        {stats.data ? (
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
      <label className="block text-sm font-semibold">
        조회 월
        <input
          aria-label="조회 월"
          type="month"
          className="field mt-2"
          value={month}
          onChange={(e) => {
            setMonth(e.target.value);
            setPage(0);
          }}
        />
      </label>
      {month && (
        <Button
          variant="ghost"
          onClick={() => {
            setMonth('');
            setPage(0);
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
              {item.hubName} → {item.destName}
            </h2>
            <div className="mt-4 flex justify-between gap-3">
              <strong>{won(item.shareAmount)}</strong>
              <span className="text-sm font-semibold text-saving">
                {won(item.savingAmount)} 절약
              </span>
            </div>
            <Link className="link mt-2 px-0" to={`/groups/${item.groupId}/fare`}>
              정산 상세
            </Link>
          </Card>
        ))
      )}
      <div className="flex items-center justify-between">
        <Button
          variant="secondary"
          disabled={page === 0 || history.isFetching}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          이전
        </Button>
        <span className="text-sm">{page + 1}쪽</span>
        <Button
          variant="secondary"
          disabled={!history.data || history.data.last || history.isFetching}
          onClick={() => setPage((p) => p + 1)}
        >
          다음
        </Button>
      </div>
    </section>
  );
}
