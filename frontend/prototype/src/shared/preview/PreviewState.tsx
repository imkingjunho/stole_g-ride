import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button, Card, Icon, Spinner } from '../ui';

export function PreviewState({
  children,
  emptyTitle = '아직 표시할 내역이 없어요',
}: {
  children: ReactNode;
  emptyTitle?: string;
}) {
  const [params, setParams] = useSearchParams();
  const state = params.get('state');
  function reset() {
    const next = new URLSearchParams(params);
    next.delete('state');
    setParams(next);
  }
  if (state === 'loading')
    return (
      <Card className="state-card">
        <Spinner />
        <p>선택한 로딩 화면 시안이에요.</p>
        <Button variant="secondary" onClick={reset}>
          정상 화면으로
        </Button>
      </Card>
    );
  if (state === 'error')
    return (
      <Card className="state-card">
        <span className="state-icon">
          <Icon name="info" />
        </span>
        <h2>잠시 연결이 어려워요</h2>
        <p>오류 안내 화면의 예시예요. 다시 시도해 주세요.</p>
        <Button onClick={reset}>
          <Icon name="refresh" />
          다시 시도
        </Button>
      </Card>
    );
  if (state === 'empty')
    return (
      <Card className="state-card">
        <span className="state-icon">
          <Icon name="ticket" />
        </span>
        <h2>{emptyTitle}</h2>
        <p>이용한 내용이 생기면 이곳에서 확인할 수 있어요.</p>
        <Button variant="secondary" onClick={reset}>
          예시 내역 보기
        </Button>
      </Card>
    );
  return <>{children}</>;
}
