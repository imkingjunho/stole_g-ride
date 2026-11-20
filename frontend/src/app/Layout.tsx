import { Link, Outlet, useLocation } from 'react-router-dom';
import { isMockMode } from '@/shared/api/config';
import { RehearsalStatus } from './rehearsal/RehearsalPanel';
import { SessionEffects } from './SessionEffects';
import { useTabBarScroll } from './useTabBarScroll';
import { useConnectionStore } from '@/shared/ws/connectionStore';
import { Badge } from '@/shared/ui';
import { LegalNotice } from '@/shared/ui/LegalNotice';
const tabs = [
  {
    to: '/request',
    label: '요청',
    path: 'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z',
  },
  { to: '/waiting', label: '대기', path: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 6v6l4 2' },
  {
    to: '/history',
    label: '이력',
    path: 'M6 3h12a1 1 0 0 1 1 1v17l-3-2-2 2-2-2-2 2-2-2-3 2V4a1 1 0 0 1 1-1ZM9 8h6M9 12h6',
  },
  {
    to: '/me',
    label: '내 정보',
    path: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM3.5 21v-2a8.5 6 0 0 1 17 0v2z',
  },
];
export function Layout() {
  const { pathname, state } = useLocation();
  const inChat = pathname.endsWith('/chat');
  const activeTab = pathname.startsWith('/groups/')
    ? state?.fromHistory
      ? '/history'
      : '/waiting'
    : pathname;
  const connection = useConnectionStore((s) => s.status);
  const tabIndex = tabs.findIndex((tab) => tab.to === activeTab);
  const tabsHidden = useTabBarScroll(pathname);
  return (
    <div
      data-tabs-hidden={tabsHidden}
      className={
        'app-frame mx-auto flex max-w-app flex-col bg-canvas ' +
        (inChat ? 'chat-frame' : 'min-h-dvh')
      }
    >
      <SessionEffects />
      <a href="#main-content" className="sr-only focus:not-sr-only focus:p-page">
        본문으로 이동
      </a>
      {!inChat && (
        <header className="flex min-h-16 items-center justify-between bg-white px-page">
          <Link to="/request" className="link text-xl font-bold text-ink">
            가치가
          </Link>
          {isMockMode && <Badge variant="dev">DEV · 목 데이터</Badge>}
        </header>
      )}
      {!inChat && import.meta.env.DEV && isMockMode && (
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
      <RehearsalStatus />
      <main id="main-content" key={activeTab} className="tab-content min-h-0 flex-1">
        <Outlet />
      </main>
      {!inChat && <LegalNotice />}
      <nav aria-label="주요 메뉴" className="floating-tabs">
        <div className="floating-tabs-track">
          <span
            className="floating-tab-pill"
            aria-hidden="true"
            style={{
              width: `${100 / tabs.length}%`,
              transform: `translateX(${Math.max(0, tabIndex) * 100}%)`,
              opacity: tabIndex < 0 ? 0 : 1,
            }}
          />
          {tabs.map((t) => (
            <Link
              key={t.to}
              to={t.to}
              aria-current={activeTab === t.to ? 'page' : undefined}
              className="floating-tab"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d={t.path} />
              </svg>
              <span>{t.label}</span>
            </Link>
          ))}
        </div>
      </nav>
    </div>
  );
}
