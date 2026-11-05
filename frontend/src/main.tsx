import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import '@/index.css';
import { router } from '@/app/router';
import { Toast } from '@/shared/ui';
import { isMockMode } from '@/shared/api/client';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 대기 상태는 WebSocket 으로 받으므로 창 포커스마다 다시 부르지 않는다
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

/** 목 모드면 MSW 를 먼저 켜고 나서 화면을 그린다. 첫 요청이 새어 나가지 않게 하기 위해서다. */
async function enableMocking() {
  if (!isMockMode) {
    return;
  }
  const { worker } = await import('@/shared/mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass' });
}

void enableMocking()
  .then(() => {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
          <Toast />
        </QueryClientProvider>
      </StrictMode>,
    );
  })
  .catch(() => {
    createRoot(document.getElementById('root')!).render(
      <main className="page mx-auto max-w-app">
        <h1 className="text-2xl font-bold">앱을 시작하지 못했어요</h1>
        <p>연결 상태를 확인하고 새로고침해 주세요.</p>
        <button className="field" onClick={() => window.location.reload()}>
          새로고침
        </button>
      </main>,
    );
  });
