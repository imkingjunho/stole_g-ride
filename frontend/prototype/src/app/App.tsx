import { useEffect, useRef, useState } from 'react';
import {
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useSearchParams,
} from 'react-router-dom';
import { AuthPage } from '../features/auth/AuthPage';
import { GroupPage, FarePage } from '../features/group/GroupPage';
import { ChatPage } from '../features/chat/ChatPage';
import { HistoryPage, ProfilePage } from '../features/history/HistoryPage';
import { Badge, BottomSheet, Icon, LegalNotice } from '../shared/ui';
import type { IconName } from '../shared/ui/Icon';

const navigation: { to: string; label: string; icon: IconName; number: string }[] = [
  { to: '/login', label: '로그인', icon: 'lock', number: '01' },
  { to: '/signup', label: '회원가입', icon: 'user', number: '02' },
  { to: '/groups/demo', label: '매칭 성사', icon: 'people', number: '03' },
  { to: '/groups/demo/fare', label: '정산 상세', icon: 'ticket', number: '04' },
  { to: '/groups/demo/chat', label: '그룹 채팅', icon: 'chat', number: '05' },
  { to: '/history', label: '이용 이력', icon: 'history', number: '06' },
];

function Brand() {
  return (
    <Link to="/groups/demo" className="brand" aria-label="가치가 매칭 성사 화면">
      <span className="brand-symbol">
        <Icon name="arrow" />
        <Icon name="arrow" />
      </span>
      <span>
        가치가<small>WE-MEET</small>
      </span>
    </Link>
  );
}

function Navigation({ close }: { close?: () => void }) {
  return (
    <nav className="navigation" aria-label="화면 미리보기">
      {navigation.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to !== '/signup'}
          onClick={close}
          className={({ isActive }) => `nav-item ${isActive ? 'nav-active' : ''}`}
        >
          <Icon name={item.icon} />
          <span>{item.label}</span>
          <small>{item.number}</small>
        </NavLink>
      ))}
    </nav>
  );
}

export function App() {
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const [menuOpen, setMenuOpen] = useState(false);
  const main = useRef<HTMLElement>(null);
  const initialPath = useRef(location.pathname);
  const authPage = location.pathname === '/login' || location.pathname.startsWith('/signup');
  useEffect(() => {
    const current =
      navigation.find((item) => item.to === location.pathname) ??
      navigation.find((item) => location.pathname.startsWith(item.to));
    document.title = `${current?.label ?? '화면'} · 가치가 미리보기`;
    if (initialPath.current !== location.pathname) {
      main.current?.focus();
      window.scrollTo({ top: 0, behavior: 'instant' });
      initialPath.current = location.pathname;
    }
  }, [location.pathname]);

  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          main.current?.focus();
        }}
      >
        본문으로 바로가기
      </a>
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-intro">
          <span className="eyebrow">CAMPUS MOBILITY</span>
          <p>
            함께 가면,
            <br />
            일상이 가벼워져요.
          </p>
        </div>
        <p className="nav-caption">화면 미리보기</p>
        <Navigation />
        <div className="sidebar-bottom">
          <span className="campus-dot" />
          전남대학교 동승 프로젝트<small>모바일 우선 · 6개 핵심 화면</small>
        </div>
      </aside>
      <div className="workspace">
        <header className="workspace-header">
          <div className="mobile-brand">
            <Brand />
          </div>
          <span className="desktop-breadcrumb">
            WE-MEET <span>/</span> 화면 디자인
          </span>
          <div className="header-actions">
            <Badge tone="amber">DEV · 화면 시안</Badge>
            <button
              className="icon-button mobile-menu"
              aria-label="화면 메뉴 열기"
              onClick={() => setMenuOpen(true)}
            >
              <Icon name="menu" />
            </button>
          </div>
        </header>
        <div className="preview-banner">
          <Icon name="info" />
          <span>예시 데이터로 보는 화면이에요. 실제 가입·매칭·전송은 발생하지 않아요.</span>
        </div>
        {!authPage && (
          <div className="preview-tools">
            <span>오늘도, 가치 있는 동행</span>
            <label>
              화면 상태{' '}
              <select
                value={params.get('state') ?? 'normal'}
                onChange={(event) => {
                  const next = new URLSearchParams(params);
                  if (event.target.value === 'normal') next.delete('state');
                  else next.set('state', event.target.value);
                  setParams(next);
                }}
              >
                <option value="normal">기본</option>
                <option value="loading">로딩</option>
                <option value="error">오류</option>
                <option value="empty">빈 화면</option>
              </select>
            </label>
          </div>
        )}
        <main
          id="main-content"
          ref={main}
          tabIndex={-1}
          className={`main-content ${authPage ? 'main-auth' : ''}`}
        >
          <Routes>
            <Route path="/" element={<Navigate to="/groups/demo" replace />} />
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/signup" element={<AuthPage mode="email" />} />
            <Route path="/signup/verify" element={<AuthPage mode="verify" />} />
            <Route path="/signup/profile" element={<AuthPage mode="profile" />} />
            <Route path="/groups/demo" element={<GroupPage />} />
            <Route path="/groups/demo/fare" element={<FarePage />} />
            <Route path="/groups/demo/chat" element={<ChatPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/me" element={<ProfilePage />} />
            <Route
              path="*"
              element={
                <div className="state-card">
                  <h1>준비 중인 화면이에요</h1>
                  <p>왼쪽 메뉴에서 준비된 화면을 선택해 주세요.</p>
                  <Link className="button button-primary" to="/groups/demo">
                    매칭 성사 화면 보기
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="workspace-footer">
          <LegalNotice />
          <span>가치가 · WE-MEET</span>
        </footer>
      </div>
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title="화면 미리보기">
        <Navigation close={() => setMenuOpen(false)} />
      </BottomSheet>
    </div>
  );
}
