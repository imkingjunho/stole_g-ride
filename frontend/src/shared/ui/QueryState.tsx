import { Button } from './Button';
import { Spinner } from './Spinner';
export function QueryState({
  loading,
  error,
  retry,
  empty,
}: {
  loading?: boolean;
  error?: unknown;
  retry?: () => void;
  empty?: string;
}) {
  if (loading)
    return (
      <div className="p-page" role="status">
        <Spinner /> <span>정보를 불러오고 있어요.</span>
      </div>
    );
  if (error)
    return (
      <div className="space-y-3 p-page">
        <p role="alert">{error instanceof Error ? error.message : '연결을 확인해 주세요.'}</p>
        {retry && (
          <Button variant="secondary" onClick={retry}>
            다시 시도
          </Button>
        )}
      </div>
    );
  return <p className="p-page text-sm text-slate-600">{empty ?? '아직 표시할 정보가 없어요.'}</p>;
}
