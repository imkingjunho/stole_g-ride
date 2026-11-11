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
  await worker.start({ onUnhandledRequest: 'bypass', quiet: !import.meta.env.DEV });

  // 강력 새로고침(Ctrl+Shift+R)으로 열린 페이지는 서비스 워커가 제어하지 않아 목 응답을 못 받는다.
  // 한 번만 일반 새로고침해서 워커가 페이지를 잡게 한다 (무한 새로고침 방지 플래그 사용).
  const reloadFlag = 'msw-controller-reload';
  if (!navigator.serviceWorker.controller) {
    if (!sessionStorage.getItem(reloadFlag)) {
      sessionStorage.setItem(reloadFlag, '1');
      window.location.reload();
      await new Promise(() => {});
    }
  } else {
    sessionStorage.removeItem(reloadFlag);
  }
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
