import './admin.css';
import { pickupName } from '@/shared/pickupName';
import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, ApiError, isMockMode, unwrap } from '@/shared/api/client';
import { koreaTime } from '@/shared/api/format';
import { useAuthStore } from '@/shared/stores/authStore';
import { Badge, Button, Card } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import type { components } from '@/generated/api';
import { EXPIRING_MINUTES, readOperations } from './operations';
import { ParticipantTable, type ParticipantView } from './ParticipantTable';

type Dashboard = components['schemas']['AdminDashboard'];

function DailyMatches({ daily }: Pick<Dashboard, 'daily'>) {
  const max = Math.max(1, ...daily.map((day) => day.groups));
  const sum = daily.reduce((total, day) => total + day.groups, 0);
  return (
    <Card className="ops-panel">
      <div className="ops-panel-head">
        <div>
          <h2>일별 매칭 추이</h2>
          <p>한국 시간 기준 · 확정된 그룹 수</p>
        </div>
        <span className="ops-total">
          최근 7일 <strong>{sum}건</strong>
        </span>
      </div>
      <ol aria-label="최근 7일 날짜별 확정 그룹" className="ops-chart">
        {daily.map(({ date, groups }, i) => (
          <li key={date} className={i === daily.length - 1 ? 'ops-today' : ''}>
            <div className="ops-plot">
              <strong>
                {groups}
                <span className="sr-only">건</span>
              </strong>
              <span
                aria-hidden="true"
                style={{ height: (groups / max) * 94, minHeight: groups ? 4 : 0 }}
              />
            </div>
            <time dateTime={date}>{date.slice(5).replace('-', '/')}</time>
          </li>
        ))}
      </ol>
    </Card>
  );
}

function EndedRequests({ rows }: { rows: Dashboard['endedByHub'] }) {
  const ranked = rows
    ?.slice()
    .sort(
      (a, b) =>
        b.expired + b.cancelled - (a.expired + a.cancelled) ||
        a.hubName.localeCompare(b.hubName, 'ko'),
    );
  return (
    <Card className="ops-panel ops-ended">
      <div className="ops-panel-head">
        <div>
          <h2>거점별 만료·취소</h2>
          <p>최근 7일에 생성된 요청 중 대기 시간이 끝났거나 사용자가 취소한 건수</p>
        </div>
      </div>
      {ranked?.length ? (
        <table aria-label="거점별 만료 및 취소 요청">
          <thead>
            <tr>
              <th>출발 거점</th>
              <th>자동 만료</th>
              <th>직접 취소</th>
              <th>합계</th>
            </tr>
          </thead>
          <tbody>
            {ranked.map((row) => (
              <tr key={row.hubName}>
                <th scope="row">{pickupName(row.hubName)}</th>
                <td>{row.expired}건</td>
                <td>{row.cancelled}건</td>
                <td>{row.expired + row.cancelled}건</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="ops-empty">
          {ranked
            ? '이 기간에 생성된 요청 중 만료되거나 취소된 건이 없어요.'
            : '현재 서버에서 만료·취소 집계를 제공하지 않아요.'}
        </p>
      )}
    </Card>
  );
}

function Workspace({
  data,
  refreshing,
  refresh,
}: {
  data: Dashboard;
  refreshing: boolean;
  refresh: () => void;
}) {
  const [view, setView] = useState<ParticipantView>('waiting');
  const [hub, setHub] = useState('');
  const [search, setSearch] = useState('');
  const [limit, setLimit] = useState(25);
  const roster = useRef<HTMLHeadingElement>(null);
  const ops = readOperations(data);
  const { queue, members, hubs } = ops;
  const time = koreaTime(data.asOf);
  const date = time.toLocaleDateString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  });
  const clock = time.toLocaleTimeString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
  const today = data.daily[data.daily.length - 1];
  function inspect(nextHub: string, nextView: ParticipantView) {
    setHub(nextHub);
    setView(nextView);
    setSearch('');
    setLimit(25);
    roster.current?.focus({ preventScroll: true });
    roster.current?.scrollIntoView({ block: 'start' });
  }
  return (
    <div className="ops-workspace">
      <header className="ops-top">
        <div className="ops-width">
          <div className="ops-brand">
            <b>가치가</b>
            <span>운영센터</span>
            {isMockMode && <Badge variant="dev">목 데이터</Badge>}
          </div>
          <Link className="ops-back" to="/me">
            내 정보로 이동 <span aria-hidden="true">↗</span>
          </Link>
        </div>
      </header>
      <div className="ops-width ops-body">
        <header className="ops-heading">
          <div>
            <p>{date}</p>
            <h1>매칭 운영 현황</h1>
          </div>
          <div className="ops-updated">
            <span>기준 {clock} · 15초마다 갱신</span>
            <Button variant="secondary" disabled={refreshing} onClick={refresh}>
              새로고침
            </Button>
          </div>
        </header>
        <dl className="ops-metrics">
          <div>
            <dt>오늘 확정된 매칭</dt>
            <dd>
              {today?.groups ?? '—'}
              <small>건</small>
            </dd>
            <p>그룹당 한 건으로 집계</p>
          </div>
          <div>
            <dt>현재 대기자</dt>
            <dd>
              {queue.length}
              <small>명</small>
            </dd>
            <p>대기 중인 거점 {hubs.filter((h) => h.waiting > 0).length}곳</p>
          </div>
          <div className={ops.expiring ? 'ops-urgent' : ''}>
            <dt>만료 임박</dt>
            <dd>
              {ops.expiresKnown ? ops.expiring : '—'}
              <small>명</small>
            </dd>
            {ops.expiresKnown ? (
              <button onClick={() => inspect('', 'expiring')}>
                남은 시간 {EXPIRING_MINUTES}분 이내 <span aria-hidden="true">→</span>
              </button>
            ) : (
              <p>일부 요청의 만료 시각 미제공</p>
            )}
          </div>
          <div>
            <dt>매칭된 참여자</dt>
            <dd>
              {members.length}
              <small>명</small>
            </dd>
            <p>
              수락 중 {members.length - ops.confirmedMembers}명 · 확정 {ops.confirmedMembers}명
            </p>
          </div>
        </dl>
        <div className="ops-panels">
          <DailyMatches daily={data.daily} />
          <Card className="ops-panel">
            <div className="ops-panel-head">
              <div>
                <h2>거점별 현황</h2>
                <p>대기가 많은 순 · 거점을 눌러 참여자 확인</p>
              </div>
            </div>
            {hubs.length ? (
              <div className="ops-hub-list">
                <table>
                  <thead>
                    <tr>
                      <th>승차 거점</th>
                      <th>대기</th>
                      <th>최장 대기</th>
                      <th>매칭 중¹</th>
                    </tr>
                  </thead>
                  <tbody>
                    {hubs.map((h) => (
                      <tr key={h.name}>
                        <th scope="row">
                          <button
                            onClick={() => inspect(h.name, h.waiting ? 'waiting' : 'matched')}
                          >
                            {h.name}
                          </button>
                        </th>
                        <td>{h.waiting}명</td>
                        <td>{h.waiting ? h.longest + '분' : '—'}</td>
                        <td>{h.matched}명</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <p className="ops-note">¹ 수락 중이거나 확정 후 탑승 완료 전인 참여자</p>
              </div>
            ) : (
              <p className="ops-empty">현재 참여자가 있는 거점이 없어요.</p>
            )}
          </Card>
        </div>
        <Card className="ops-panel">
          <div className="ops-panel-head">
            <div>
              <h2 ref={roster} tabIndex={-1}>
                현재 참여자
              </h2>
              <p>수락 대기 중인 그룹 {ops.pendingGroups}개</p>
            </div>
          </div>
          <div className="ops-filters">
            <div className="ops-toggle" role="group" aria-label="참여자 상태">
              {(['waiting', 'expiring', 'matched'] as const).map((key) => (
                <button
                  key={key}
                  aria-pressed={key === view}
                  onClick={() => {
                    setView(key);
                    setLimit(25);
                  }}
                >
                  {{ waiting: '대기자', expiring: '만료 임박', matched: '매칭된 사람' }[key]}
                </button>
              ))}
            </div>
            <label>
              <span className="sr-only">거점 필터</span>
              <select
                value={hub}
                onChange={(e) => {
                  setHub(e.target.value);
                  setLimit(25);
                }}
              >
                <option value="">전체 거점</option>
                {hub && !hubs.some((h) => h.name === hub) && <option>{hub}</option>}
                {hubs.map((h) => (
                  <option key={h.name}>{h.name}</option>
                ))}
              </select>
            </label>
            <label>
              <span className="sr-only">닉네임 검색</span>
              <input
                type="search"
                placeholder="닉네임 검색"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setLimit(25);
                }}
              />
            </label>
          </div>
          <ParticipantTable
            ops={ops}
            view={view}
            hub={hub}
            search={search}
            limit={limit}
            onMore={() => setLimit(limit + 25)}
          />
        </Card>
        <EndedRequests rows={data.endedByHub} />
      </div>
    </div>
  );
}

export function AdminPage() {
  const version = useAuthStore((s) => s.sessionVersion);
  const userId = useAuthStore((s) => s.user?.id);
  const query = useQuery({
    queryKey: ['admin-dashboard', userId, version],
    queryFn: async ({ signal }) => {
      const r = await api.GET('/api/admin/dashboard', { signal });
      return unwrap(r.data, r.error);
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
  return (
    <Workspace
      data={query.data}
      refreshing={query.isFetching}
      refresh={() => void query.refetch()}
    />
  );
}
