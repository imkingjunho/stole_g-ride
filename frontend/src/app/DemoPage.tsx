import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate, Link } from 'react-router-dom';
import { Button, Card, Badge } from '@/shared/ui';
import { mockState, resetGroup, notifyMatch, createMockRequest } from '@/shared/mocks/state';
import { mockGroup, mockRideRequest } from '@/shared/mocks/data';
import { GroupRouteMap } from '@/features/group/GroupRouteMap';
export function DemoPage() {
  const [estimated, setEstimated] = useState(false);
  const sample = useMemo(() => ({ ...mockGroup, estimated }), [estimated]);
  const qc = useQueryClient();
  const navigate = useNavigate();
  function open(pending: boolean) {
    resetGroup(pending ? 'PENDING' : 'CONFIRMED');
    qc.removeQueries({ queryKey: ['group'] });
    qc.removeQueries({ queryKey: ['messages'] });
    navigate('/groups/17');
  }
  function openExpiredRequest() {
    const fixture = mockRideRequest;
    createMockRequest({
      hubId: fixture.hub.id,
      destName: fixture.destName,
      destLat: fixture.destLat,
      destLng: fixture.destLng,
      departAt: new Date().toISOString(),
      maxWaitMin: 5,
      sameGenderOnly: true,
      maxDetourRatio: 0.2,
    });
    mockState.request!.expiresAt = new Date(Date.now() - 1000).toISOString();
    qc.removeQueries({ queryKey: ['myRequest'] });
    navigate('/waiting');
  }
  return (
    <section className="page">
      <Badge variant="dev" />
      <h1 className="text-2xl font-bold">프론트 기능 확인</h1>
      <p className="text-sm text-slate-600">
        아래 동작은 목 데이터에만 적용돼요. 성사·수락·정산·채팅 흐름을 확인할 수 있어요.
      </p>
      <Card className="space-y-3">
        <h2 className="font-bold">요청 · 예외 상태 확인</h2>
        <Button
          fullWidth
          variant="secondary"
          onClick={() => {
            mockState.request = null;
            qc.removeQueries({ queryKey: ['myRequest'] });
            navigate('/request');
          }}
        >
          새 요청 작성
        </Button>
        <Button fullWidth variant="secondary" onClick={openExpiredRequest}>
          대기 만료 화면 보기
        </Button>
        <Button
          fullWidth
          variant="secondary"
          onClick={() => {
            mockState.failures.set('GET /api/stats/me', 1);
            qc.removeQueries({ queryKey: ['stats'] });
            navigate('/history');
          }}
        >
          통계 오류와 재시도 보기
        </Button>
      </Card>
      <Card>
        <h2 className="font-bold">샘플 경로 지도 확인</h2>
        <p className="mt-2 text-sm text-slate-600">
          전남대 후문에서 함께 타고 경신여고·유스퀘어·광주송정역 순서로 내려요. 경로선 3개와 탑승
          1개·하차 3개 마커가 모두 보이는지 확인해 주세요.
        </p>
        <label className="mt-3 flex min-h-touch items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={estimated}
            onChange={(e) => setEstimated(e.target.checked)}
          />
          추정 경로 점선 확인
        </label>
        <GroupRouteMap group={sample} />
        <details className="text-sm">
          <summary className="flex min-h-touch cursor-pointer items-center">
            샘플 경로 JSON 보기
          </summary>
          <pre className="max-h-64 overflow-auto rounded-control bg-canvas p-3 text-xs">
            {JSON.stringify(sample, null, 2)}
          </pre>
        </details>
      </Card>
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
