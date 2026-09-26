import { Navigate, createBrowserRouter } from 'react-router-dom';
import { RouteError } from './RouteError';
import { Layout } from '@/app/Layout';
import { RequireAuth, GuestOnly } from './RouteGuards';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProfilePage } from '@/features/auth/ProfilePage';
import { SignupPage } from '@/features/auth/SignupPage';
import { VerifyPage } from '@/features/auth/VerifyPage';
import { ChatPage } from '@/features/chat/ChatPage';
import { GroupPage } from '@/features/group/GroupPage';
import { FarePage } from '@/features/group/FarePage';
import { HistoryPage } from '@/features/history/HistoryPage';
import { MyPage } from '@/features/history/MyPage';
import { RequestPage } from '@/features/request/RequestPage';
import { WaitingPage } from '@/features/request/WaitingPage';
import { DesignSystemPage } from './DesignSystemPage';
import { DemoPage } from './DemoPage';
import { isMockMode } from '@/shared/api/config';
export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    errorElement: <RouteError />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/signup/verify', element: <VerifyPage /> },
      { path: '/signup/profile', element: <ProfilePage /> },
    ],
  },
  {
    element: <RequireAuth />,
    errorElement: <RouteError />,
    children: [
      {
        path: '/',
        element: <Layout />,
        children: [
          { index: true, element: <Navigate to="/request" replace /> },
          { path: 'request', element: <RequestPage /> },
          { path: 'waiting', element: <WaitingPage /> },
          { path: 'groups/:groupId', element: <GroupPage /> },
          { path: 'groups/:groupId/fare', element: <FarePage /> },
          { path: 'groups/:groupId/chat', element: <ChatPage /> },
          { path: 'history', element: <HistoryPage /> },
          { path: 'me', element: <MyPage /> },
          ...(import.meta.env.DEV && isMockMode
            ? [
                { path: 'dev/demo', element: <DemoPage /> },
                { path: 'dev/ui', element: <DesignSystemPage /> },
              ]
            : []),
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/request" replace /> },
]);
