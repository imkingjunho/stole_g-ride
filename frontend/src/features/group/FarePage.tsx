import { Link } from 'react-router-dom';
import { Card, Badge } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { won } from '@/shared/api/format';
import { useGroup } from './useGroup';
export function FarePage() {
  const q = useGroup();
  const g = q.data;
  const me = g?.members.find((m) => m.isMe);
  if (q.error || !g)
    return <QueryState loading={q.isLoading} error={q.error} retry={() => void q.refetch()} />;
  const totalSoloDistance = g.members.reduce((sum, member) => sum + member.soloDistance, 0);
  return (
    <section className="page">
      <Link className="link px-0" to={`/groups/${g.groupId}`}>
        ← 동승 정보
      </Link>
      <header>
        <h1 className="text-2xl font-bold">내 요금, 이렇게 나눴어요</h1>
        <p className="mt-2 text-sm text-slate-600">
          혼자 이동할 거리의 비율로 나누고, 혼자 탈 때 요금을 넘지 않도록 조정해요.
        </p>
      </header>
      <Card>
        {g.estimated && <Badge variant="estimated" />}
        <p className="text-sm text-slate-600">내 최종 분담액</p>
        <p className="mt-2 text-3xl font-bold">{me ? won(me.shareAmount) : '확인 중'}</p>
      </Card>
      {me && (
        <Card className="space-y-4">
          <h2 className="font-bold">거리 비율로 계산해요</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between gap-3">
              <dt>내 단독 이동 거리</dt>
              <dd>{(me.soloDistance / 1000).toLocaleString('ko-KR')}km</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt>모두의 단독 거리 합</dt>
              <dd>{(totalSoloDistance / 1000).toLocaleString('ko-KR')}km</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt>상한 조정 전 내 거리 비율</dt>
              <dd>
                {totalSoloDistance > 0
                  ? `${((me.soloDistance / totalSoloDistance) * 100).toFixed(1)}%`
                  : '확인 중'}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt>내 단독 예상 요금 · 상한</dt>
              <dd>{won(me.soloFare)}</dd>
            </div>
            <div className="flex justify-between gap-3 font-semibold">
              <dt>내 예상 절감액</dt>
              <dd>{won(me.savingAmount)}</dd>
            </div>
          </dl>
          <p className="border-t border-line pt-4 text-sm text-slate-600">
            거리 비율로 나눈 금액이 누군가의 단독요금을 넘으면, 초과분을 나머지 동승자의 거리 비율로
            다시 나눠요. 남은 원 단위 금액을 보정해 모두의 분담액 합을 총 예상 요금에 맞춰요.
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
