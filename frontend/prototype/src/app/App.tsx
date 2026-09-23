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
import { BottomSheet, Icon, LegalNotice } from '../shared/ui';
import type { IconName } from '../shared/ui/Icon';

const navigation: { to: string; label: string; icon: IconName }[] = [
  { to: '/login', label: '로그인', icon: 'lock' },
  { to: '/signup', label: '회원가입', icon: 'user' },
  { to: '/groups/demo', label: '매칭 성사', icon: 'people' },
  { to: '/groups/demo/fare', label: '정산 상세', icon: 'ticket' },
  { to: '/groups/demo/chat', label: '그룹 채팅', icon: 'chat' },
  { to: '/history', label: '이용 이력', icon: 'history' },
  { to: '/me', label: '내 정보', icon: 'user' },
];
const tabs: { to: string; label: string; icon: IconName }[] = [
  { to: '/groups/demo', label: '동승', icon: 'car' },
  { to: '/groups/demo/chat', label: '채팅', icon: 'chat' },
  { to: '/history', label: '이력', icon: 'history' },
  { to: '/me', label: '내 정보', icon: 'user' },
];

export function App() {
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const [menuOpen, setMenuOpen] = useState(false);
  const main = useRef<HTMLElement>(null);
  const initialPath = useRef(location.pathname);
  const path = location.pathname;
  const authPage = path === '/login' || path.startsWith('/signup');
  const chatPage = path === '/groups/demo/chat';
  const current = navigation.find((item) => item.to === path);
  const title =
    path === '/groups/demo/fare'
      ? '정산 상세 내역'
      : chatPage
        ? '동행 채팅'
        : (current?.label ?? '가치가');
  const backTo =
    path === '/groups/demo/fare' || chatPage
      ? '/groups/demo'
      : path === '/signup/verify'
        ? '/signup'
        : path === '/signup/profile'
          ? '/signup/verify'
          : '/login';
  const showBack = path === '/groups/demo/fare' || chatPage || path.startsWith('/signup');

  useEffect(() => {
    document.title = `${title} · 가치가 미리보기`;
    if (initialPath.current !== path) {
      main.current?.focus();
      window.scrollTo({ top: 0, behavior: 'instant' });
      initialPath.current = path;
    }
  }, [path, title]);

  return (
    <div className={`app-shell ${authPage ? 'auth-shell' : ''} ${chatPage ? 'chat-shell' : ''}`}>
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
      <header className="app-header">
        {showBack && (
          <Link to={backTo} className="icon-button" aria-label="이전 화면으로">
            <Icon name="back" />
          </Link>
        )}
        {authPage ? (
          <span className="campus-label">
            <i />
            전남대학교 캠퍼스 전용
          </span>
        ) : (
          <div className="header-title">
            <strong>{title}</strong>
            {chatPage && <small>후문산책러 · 초록발걸음 · 노을따라</small>}
          </div>
        )}
        <button
          className="icon-button menu-button"
          aria-label="화면 메뉴 열기"
          onClick={() => setMenuOpen(true)}
        >
          <Icon name="menu" />
        </button>
      </header>
      <div className="preview-notice">
        <span>시안</span>예시 데이터 · 실제 가입·매칭·전송 없음
      </div>
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
                <p>상단 메뉴에서 준비된 화면을 선택해 주세요.</p>
                <Link className="button button-primary" to="/groups/demo">
                  매칭 성사 화면 보기
                </Link>
              </div>
            }
          />
        </Routes>
      </main>
      <footer className="app-footer">
        <LegalNotice />
        <span>가치가 · WE-MEET</span>
      </footer>
      {!authPage && (
        <nav className="bottom-nav" aria-label="주요 화면">
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end
              className={({ isActive }) =>
                `bottom-tab ${isActive || (tab.to === '/groups/demo' && path.endsWith('/fare')) ? 'tab-active' : ''}`
              }
            >
              <Icon name={tab.icon} />
              <span>{tab.label}</span>
            </NavLink>
          ))}
        </nav>
      )}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title="화면 미리보기">
        <p className="muted small">준비된 화면과 안내 상태를 확인할 수 있어요.</p>
        <nav className="screen-menu" aria-label="화면 미리보기">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) => `menu-row ${isActive ? 'menu-active' : ''}`}
            >
              <Icon name={item.icon} />
              <span>{item.label}</span>
              <Icon name="chevron" />
            </NavLink>
          ))}
        </nav>
        {!authPage && (
          <label className="preview-control">
            화면 상태
            <select
              value={params.get('state') ?? 'normal'}
              onChange={(event) => {
                const next = new URLSearchParams(params);
                if (event.target.value === 'normal') next.delete('state');
                else next.set('state', event.target.value);
                setParams(next);
                setMenuOpen(false);
              }}
            >
              <option value="normal">기본</option>
              <option value="loading">로딩</option>
              <option value="error">오류</option>
              <option value="empty">빈 화면</option>
            </select>
          </label>
        )}
      </BottomSheet>
    </div>
  );
}
