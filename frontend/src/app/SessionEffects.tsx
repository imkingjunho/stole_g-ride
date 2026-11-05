import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/shared/stores/authStore';
import { useToastStore } from '@/shared/stores/toastStore';
import type { components } from '@/generated/api';
import { useConnectionStore } from '@/shared/ws/connectionStore';
import { stompClient, isMatchNotification, isQueueStatus } from '@/shared/ws/stompClient';
export function SessionEffects() {
  const userId = useAuthStore((s) => s.user?.id);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);
  const status = useConnectionStore((s) => s.status);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  useEffect(() => {
    stompClient.connect();
    const offStatus = stompClient.subscribe('/user/queue/status', (value) => {
      if (!isQueueStatus(value)) return;
      const current = queryClient.getQueryData<components['schemas']['RideRequestDetail'] | null>([
        'myRequest',
      ]);
      // 취소·새 요청 뒤 늦게 도착한 이전 요청의 push는 적용하지 않는다.
      if (!current || current.requestId !== value.requestId) return;
      if (current.status !== 'WAITING' || value.status !== 'WAITING') {
        // QueueStatus에는 groupId가 없다. 상태 전환은 상세 REST를 다시 조회한다.
        void queryClient.invalidateQueries({ queryKey: ['myRequest'], exact: true });
        return;
      }
      queryClient.setQueryData(['myRequest'], { ...current, ...value });
    });
    const off = stompClient.subscribe('/user/queue/match', (value) => {
      if (!isMatchNotification(value)) return;
      void queryClient.invalidateQueries({ queryKey: ['myRequest'], exact: true });
      void queryClient.invalidateQueries({ queryKey: ['group', value.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['history'] });
      void queryClient.invalidateQueries({ queryKey: ['stats'] });
      useToastStore.getState().notify(value.message);
      if (value.type === 'DISSOLVED') navigate('/waiting', { replace: true });
      else if (value.type === 'PROPOSED' || value.type === 'CONFIRMED')
        navigate(`/groups/${value.groupId}`);
    });
    return () => {
      offStatus();
      off();
      stompClient.disconnect();
      queryClient.clear();
    };
  }, [userId, sessionVersion, queryClient, navigate]);
  useEffect(() => {
    if (status !== 'connected') return;
    // 단절 중 놓친 push는 재연결 직후 상세 조회로 복구한다.
    void queryClient.invalidateQueries({ queryKey: ['myRequest'], exact: true });
    void queryClient.invalidateQueries({ queryKey: ['group'] });
  }, [status, queryClient]);
  return null;
}
