import { Navigate, createBrowserRouter } from 'react-router-dom';
import { Layout } from '@/app/Layout';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProfilePage } from '@/features/auth/ProfilePage';
import { SignupPage } from '@/features/auth/SignupPage';
import { VerifyPage } from '@/features/auth/VerifyPage';
import { ChatPage } from '@/features/chat/ChatPage';
import { GroupPage } from '@/features/group/GroupPage';
import { HistoryPage } from '@/features/history/HistoryPage';
import { MyPage } from '@/features/history/MyPage';
import { RequestPage } from '@/features/request/RequestPage';
import { WaitingPage } from '@/features/request/WaitingPage';

/**
 * 라우트 표 (PRD §14.6). Phase 0 에 전부 등록해 두고 화면은 placeholder 로 채운다.
 *
 * 라우트 가드(인증 필요 화면은 /login 으로)는 오승원이 T1-3 에서 넣는다.
 */
export const router = createBrowserRouter([
  // 인증 화면은 하단 탭 없이 단독으로 띄운다
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/signup/verify', element: <VerifyPage /> },
  { path: '/signup/profile', element: <ProfilePage /> },
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/request" replace /> },
      { path: 'request', element: <RequestPage /> },
      { path: 'waiting', element: <WaitingPage /> },
      { path: 'groups/:groupId', element: <GroupPage /> },
      { path: 'groups/:groupId/chat', element: <ChatPage /> },
      { path: 'history', element: <HistoryPage /> },
      { path: 'me', element: <MyPage /> },
    ],
  },
  // 없는 주소는 요청 화면으로
  { path: '*', element: <Navigate to="/request" replace /> },
]);
