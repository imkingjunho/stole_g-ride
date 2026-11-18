import { useEffect } from 'react';
import { useToastStore } from '../stores/toastStore';
import { Button } from './Button';

/** 새 알림마다 3초를 센다. 교체·언마운트 시 이전 타이머를 정리한다. */
export function Toast() {
  const { messages, dismiss } = useToastStore();
  const activeId = messages[0]?.id;
  useEffect(() => {
    if (activeId === undefined) return;
    const timer = window.setTimeout(() => dismiss(activeId), 3000);
    return () => window.clearTimeout(timer);
  }, [activeId, dismiss]);
  return (
    <div
      aria-label="알림"
      className="pointer-events-none fixed inset-x-4 top-4 z-50 mx-auto flex max-w-md flex-col gap-2"
    >
      {messages.map(({ id, message, tone }) => (
        <div
          key={id}
          className="pointer-events-auto flex items-center gap-3 rounded-control border border-line bg-white p-3 shadow-lg"
        >
          <p
            role={tone === 'error' ? 'alert' : 'status'}
            className={`min-w-0 flex-1 break-words text-sm ${tone === 'error' ? 'text-danger' : 'text-ink'}`}
          >
            {message}
          </p>
          <Button variant="ghost" aria-label="알림 닫기" onClick={() => dismiss(id)}>
            닫기
          </Button>
        </div>
      ))}
    </div>
  );
}
