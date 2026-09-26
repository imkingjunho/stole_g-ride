import { useToastStore } from '../stores/toastStore';
import { Button } from './Button';

/** 오류를 읽기 전에 사라지지 않도록 사용자가 직접 닫는다. 앱에 한 번 배치한다. */
export function Toast() {
  const { messages, dismiss } = useToastStore();
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
