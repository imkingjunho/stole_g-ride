import { pickupName } from '@/shared/pickupName';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, ApiError, unwrap } from '@/shared/api/client';
import { koreaTime } from '@/shared/api/format';
import { useAuthStore } from '@/shared/stores/authStore';
import { Badge, Button, Card } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import type { components } from '@/generated/api';

type Dashboard = components['schemas']['AdminDashboard'];
type Roster = 'waiting' | 'matched';

function minutesWaiting(requestedAt: string, asOf: string) {
  return Math.max(
    0,
    Math.floor((koreaTime(asOf).getTime() - koreaTime(requestedAt).getTime()) / 60000),
  );
}

function DailyMatches({ daily }: Pick<Dashboard, 'daily'>) {
  const max = Math.max(1, ...daily.map((day) => day.groups));
  return (
    <Card>
      <h2 className="font-bold">날짜별 매칭 수</h2>
      <p className="mt-1 text-sm text-muted">최근 7일 · 전원이 수락한 그룹당 1건</p>
      <ol className="mt-5 flex items-end gap-2">
        {daily.map(({ date, groups }, i) => (
          <li key={date} className="min-w-0 flex-1 text-center">
            <div className="flex h-24 flex-col justify-end">
              <span className="text-xs font-semibold tabular-nums">
                {groups}
                <span className="sr-only">건</span>
              </span>
              <div
                aria-hidden
                className={
                  i === daily.length - 1
                    ? 'mx-auto w-4 rounded-t bg-brand'
                    : 'mx-auto w-4 rounded-t bg-slate-300'
                }
                style={{ height: 2 + (68 * groups) / max }}
              />
            </div>
            <time dateTime={date} className="mt-2 block text-xs text-slate-600">
              {date.slice(5).replace('-', '/')}
            </time>
          </li>
        ))}
      </ol>
    </Card>
  );
}

export function AdminPage() {
  const [roster, setRoster] = useState<Roster>('waiting');
  const version = useAuthStore((s) => s.sessionVersion);
  const userId = useAuthStore((s) => s.user?.id);
  const query = useQuery({
    queryKey: ['admin-dashboard', userId, version],
    queryFn: async ({ signal }) => {
      const result = await api.GET('/api/admin/dashboard', { signal });
      return unwrap(result.data, result.error);
    },
    retry: false,
    refetchInterval: (q) =>
      q.state.error instanceof ApiError && q.state.error.code === 'FORBIDDEN' ? false : 15000,
    gcTime: 0,
  });
  if (query.error instanceof ApiError && query.error.code === 'FORBIDDEN')
    return (
      <section className="page">
        <h1 className="text-xl font-bold">관리자 권한이 필요해요</h1>
        <Link className="link" to="/me">
          내 정보로 돌아가기
        </Link>
      </section>
    );
  if (query.isError || !query.data)
    return (
      <QueryState
        loading={query.isLoading}
        error={query.error}
        retry={() => void query.refetch()}
      />
    );
  const { waiting, matched, daily, asOf } = query.data;
  const confirmed = matched.filter((person) => person.confirmed).length;
  const asOfTime = koreaTime(asOf).toLocaleTimeString('ko-KR', {
    hour12: false,
    timeZone: 'Asia/Seoul',
  });
  return (
    <section className="page">
      <header className="flex items-center justify-between gap-2">
        <div>
          <h1 className="text-title font-bold">매칭 운영 현황</h1>
          <p className="mt-1 text-xs text-muted">{asOfTime} 기준 · 15초마다 갱신</p>
        </div>
        <Button
          variant="secondary"
          disabled={query.isFetching}
          onClick={() => void query.refetch()}
        >
          새로고침
        </Button>
      </header>
      <Card>
        <dl className="grid grid-cols-3 divide-x divide-line text-center">
          {Object.entries({
            '대기 중': waiting.length,
            '수락 중': matched.length - confirmed,
            확정: confirmed,
          }).map(([label, count]) => (
            <div key={label}>
              <dt className="text-sm text-slate-600">{label}</dt>
              <dd className="mt-2 text-2xl font-bold tabular-nums">
                {count}
                <span className="ml-0.5 text-xs font-normal">명</span>
              </dd>
            </div>
          ))}
        </dl>
      </Card>
      <DailyMatches daily={daily} />
      <Card>
        <h2 className="font-bold">현재 참여자</h2>
        <div className="mt-3 flex rounded-control bg-canvas p-1">
          {(['waiting', 'matched'] as const).map((key) => (
            <button
              type="button"
              key={key}
              aria-pressed={roster === key}
              onClick={() => setRoster(key)}
              className={`min-h-touch flex-1 rounded-control text-sm font-bold ${roster === key ? 'bg-white text-brand shadow-sm' : 'text-slate-600'}`}
            >
              {key === 'waiting' ? `대기자 ${waiting.length}` : `매칭된 사람 ${matched.length}`}
            </button>
          ))}
        </div>
        {roster === 'waiting' ? (
          <>
            {waiting.length === 0 ? (
              <p className="py-5 text-sm text-muted">현재 기다리는 사람이 없어요.</p>
            ) : (
              <ul className="divide-y divide-line">
                {waiting.map((person) => (
                  <li
                    key={person.nickname + person.requestedAt}
                    className="flex items-center justify-between gap-2 py-3"
                  >
                    <div>
                      <p className="font-semibold">{person.nickname}</p>
                      <p className="text-sm text-muted">{pickupName(person.hubName)}</p>
                    </div>
                    <Badge>{minutesWaiting(person.requestedAt, asOf)}분 대기</Badge>
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : matched.length === 0 ? (
          <p className="py-5 text-sm text-muted">진행 중인 매칭이 없어요.</p>
        ) : (
          <ul className="divide-y divide-line">
            {matched.map((person) => (
              <li
                key={person.groupId + ':' + person.nickname}
                className="flex items-center justify-between gap-2 py-3"
              >
                <div>
                  <p className="font-semibold">{person.nickname}</p>
                  <p className="text-sm text-muted">
                    {pickupName(person.hubName)} · 그룹 {person.groupId}
                  </p>
                </div>
                <Badge variant={person.confirmed ? 'success' : 'estimated'}>
                  {person.confirmed ? '확정' : '수락 중'}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </section>
  );
}
