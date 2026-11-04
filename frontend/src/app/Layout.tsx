import { Link, Outlet, useLocation } from 'react-router-dom';
import { isMockMode } from '@/shared/api/config';
import { SessionEffects } from './SessionEffects';
import { useConnectionStore } from '@/shared/ws/connectionStore';
import { Badge } from '@/shared/ui';
import { LegalNotice } from '@/shared/ui/LegalNotice';
const tabs = [
  { to: '/request', label: '요청' },
  { to: '/waiting', label: '대기' },
  { to: '/history', label: '이력' },
  { to: '/me', label: '내 정보' },
];
export function Layout() {
  const { pathname } = useLocation();
  const connection = useConnectionStore((s) => s.status);
  return (
    <div className="mx-auto flex min-h-dvh max-w-app flex-col bg-canvas pb-[calc(72px+env(safe-area-inset-bottom))]">
      <SessionEffects />
      <a href="#main-content" className="sr-only focus:not-sr-only focus:p-page">
        본문으로 이동
      </a>
      <header className="flex min-h-16 items-center justify-between bg-white px-page">
        <Link to="/request" className="link text-xl font-bold text-ink">
          가치가
        </Link>
        {isMockMode && <Badge variant="dev">DEV · 목 데이터</Badge>}
      </header>
      {import.meta.env.DEV && isMockMode && (
        <Link className="link bg-brand-soft" to="/dev/demo">
          DEV · 기능 체험
        </Link>
      )}
      {pathname === '/waiting' && connection !== 'connected' && (
        <p
          role="status"
          className="mx-page mt-3 rounded-control bg-amber-100 p-3 text-sm text-amber-900"
        >
          {connection === 'connecting'
            ? '실시간 상태를 연결하고 있어요.'
            : '실시간 연결이 끊겼어요. 다시 연결하는 동안 대기 상태를 주기적으로 확인해요.'}
        </p>
      )}
      <main id="main-content" className="flex-1">
        <Outlet />
      </main>
      <LegalNotice />
      <nav
        aria-label="주요 메뉴"
        className="fixed inset-x-0 bottom-0 z-20 mx-auto flex max-w-app border-t border-line bg-white pb-[env(safe-area-inset-bottom)]"
      >
        {tabs.map((t) => (
          <Link
            key={t.to}
            to={t.to}
            aria-current={pathname.startsWith(t.to) ? 'page' : undefined}
            className={`flex min-h-16 flex-1 items-center justify-center text-sm font-semibold ${pathname.startsWith(t.to) ? 'text-brand' : 'text-slate-600'}`}
          >
            {t.label}
          </Link>
        ))}
      </nav>
    </div>
  );
}
