import { Link, Outlet, useLocation } from 'react-router-dom';
import { isMockMode } from '@/shared/api/client';

const TABS = [
  { to: '/request', label: '요청' },
  { to: '/waiting', label: '대기' },
  { to: '/history', label: '이력' },
  { to: '/me', label: '내 정보' },
];

/**
 * 공통 레이아웃 — Phase 0 뼈대.
 *
 * 모바일 우선이라 폭을 제한하고 하단 탭을 둔다. 실제 디자인은 오승원이 T1-3 에서 만든다.
 * 하단에 법적 고지 자리를 둔다 (PRD §2.2 — 중개가 아니라 동승자 찾기 도구임을 밝힌다).
 */
export function Layout() {
  const { pathname } = useLocation();

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-white">
      <header className="flex items-center justify-between border-b px-4 py-3">
        <Link to="/request" className="font-bold">
          가치가
        </Link>
        {isMockMode ? (
          <span className="rounded bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-900">
            MOCK
          </span>
        ) : null}
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <nav className="flex border-t">
        {TABS.map((tab) => (
          <Link
            key={tab.to}
            to={tab.to}
            className={`min-h-touch flex-1 py-3 text-center text-sm ${
              pathname.startsWith(tab.to) ? 'font-semibold text-slate-900' : 'text-slate-500'
            }`}
          >
            {tab.label}
          </Link>
        ))}
      </nav>

      <footer className="border-t px-4 py-3 text-center text-[11px] leading-relaxed text-slate-500">
        가치가는 동승자를 찾아 주는 도구입니다. 택시 호출·배차·결제를 대행하지 않으며,
        요금은 탑승자끼리 직접 정산합니다.
      </footer>
    </div>
  );
}
