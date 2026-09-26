import { useQueryClient } from '@tanstack/react-query';
import { useNavigate, Link } from 'react-router-dom';
import { Button, Card, Badge } from '@/shared/ui';
import { mockState, resetGroup, notifyMatch } from '@/shared/mocks/state';
export function DemoPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  function open(pending: boolean) {
    resetGroup(pending ? 'PENDING' : 'CONFIRMED');
    qc.removeQueries({ queryKey: ['group'] });
    qc.removeQueries({ queryKey: ['messages'] });
    navigate('/groups/17');
  }
  return (
    <section className="page">
      <Badge variant="dev" />
      <h1 className="text-2xl font-bold">프론트 기능 확인</h1>
      <p className="text-sm text-slate-600">
        아래 동작은 목 데이터에만 적용돼요. 요청·대기·지도는 담당 팀원의 구현을 기다리고 있어요.
      </p>
      <Card className="space-y-3">
        <Button fullWidth onClick={() => open(false)}>
          성사 화면 보기
        </Button>
        <Button fullWidth variant="secondary" onClick={() => open(true)}>
          60초 수락 화면 보기
        </Button>
        <Button
          fullWidth
          variant="secondary"
          onClick={() => {
            mockState.group.status = 'DISSOLVED';
            notifyMatch('DISSOLVED', '목 시나리오: 그룹이 해체됐어요.');
          }}
        >
          해체 알림 확인
        </Button>
        <Link className="link" to="/history">
          이력 확인
        </Link>
        <Link className="link" to="/dev/ui">
          공용 UI 확인
        </Link>
      </Card>
    </section>
  );
}
