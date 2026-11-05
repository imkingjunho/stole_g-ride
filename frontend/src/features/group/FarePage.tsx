import { Link } from 'react-router-dom';
import { Card, Badge } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { won } from '@/shared/api/format';
import { useGroup } from './useGroup';
export function FarePage() {
  const q = useGroup();
  const g = q.data;
  const me = g?.members.find((m) => m.isMe);
  if (!g)
    return <QueryState loading={q.isLoading} error={q.error} retry={() => void q.refetch()} />;
  return (
    <section className="page">
      <Link className="link px-0" to={`/groups/${g.groupId}`}>
        ← 동승 정보
      </Link>
      <header>
        <h1 className="text-2xl font-bold">내 요금, 이렇게 나눴어요</h1>
        <p className="mt-2 text-sm text-slate-600">
          함께 탄 구간의 요금을 해당 구간의 동승자끼리 나눠요.
        </p>
      </header>
      <Card>
        {g.estimated && <Badge variant="estimated" />}
        <p className="text-sm text-slate-600">내 최종 분담액</p>
        <p className="mt-2 text-3xl font-bold">{me ? won(me.shareAmount) : '확인 중'}</p>
      </Card>
      {me?.breakdown?.length ? (
        <Card className="space-y-5">
          <h2 className="font-bold">구간별 내역</h2>
          {me.breakdown.map((item) => (
            <div key={item.sectionIndex}>
              <div className="mb-2 flex justify-between text-sm">
                <span>
                  구간 {item.sectionIndex + 1} · {item.onboardCount}명
                </span>
                <strong>{won(item.share)}</strong>
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-canvas">
                <div
                  className="h-full rounded-full bg-brand"
                  style={{
                    width: `${Math.max(4, Math.min(100, ((g.route.sections[item.sectionIndex]?.distance ?? 0) / Math.max(1, g.totalDistance)) * 100))}%`,
                  }}
                />
              </div>
              <p className="mt-2 text-sm text-slate-600">
                구간 요금 {won(item.sectionFare)} ÷ {item.onboardCount}명
              </p>
            </div>
          ))}
          <p className="border-t border-line pt-4 text-sm">
            100원 단위·총액 보정:{' '}
            {me.roundingAdjustment === undefined
              ? '서버 내역 확인 중'
              : `${me.roundingAdjustment > 0 ? '+' : ''}${won(me.roundingAdjustment)}`}
          </p>
        </Card>
      ) : (
        <Card>
          <p className="text-sm text-slate-600">
            구간별 정산 내역을 준비 중이에요. 최종 분담액은 위에서 확인해 주세요.
          </p>
        </Card>
      )}
      <Card>
        <h2 className="font-bold">동승자별 분담액</h2>
        <ul className="mt-4 space-y-3">
          {g.members.map((m) => (
            <li key={m.userId} className="flex justify-between gap-3 text-sm">
              <span>
                {m.nickname} {m.isMe && '· 나'}
              </span>
              <strong>{won(m.shareAmount)}</strong>
            </li>
          ))}
        </ul>
        <p className="mt-4 border-t border-line pt-4 font-semibold">
          총 예상 요금 {won(g.totalFare)}
        </p>
      </Card>
      <p className="text-sm text-slate-600">
        실제 요금은 교통 상황에 따라 달라질 수 있어요. 이 화면은 요금 분담 계산 안내예요.
      </p>
    </section>
  );
}
