import { Placeholder } from '@/app/Placeholder';

export function WaitingPage() {
  return (
    <Placeholder title="대기" owner="송준호" task="T1-7 (FR-09·FR-10)">
      남은 시간 카운트다운과 후보 인원 수. WebSocket /user/queue/status 로 갱신한다.
    </Placeholder>
  );
}
