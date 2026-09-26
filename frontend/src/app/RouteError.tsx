import { Link } from 'react-router-dom';
import { Button } from '@/shared/ui';
export function RouteError() {
  return (
    <main className="page mx-auto min-h-dvh max-w-app">
      <h1 className="text-2xl font-bold">화면을 불러오지 못했어요</h1>
      <p className="text-sm text-slate-600">연결 상태를 확인하고 다시 시도해 주세요.</p>
      <Button onClick={() => window.location.reload()}>새로고침</Button>
      <Link className="link" to="/login">
        로그인 화면으로
      </Link>
    </main>
  );
}
