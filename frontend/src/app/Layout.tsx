import { Link, Outlet, useLocation } from 'react-router-dom';
import { isMockMode } from '@/shared/api/config';
import { SessionEffects } from './SessionEffects';
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
