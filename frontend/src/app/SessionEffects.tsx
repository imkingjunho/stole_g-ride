import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/shared/stores/authStore';
import { useToastStore } from '@/shared/stores/toastStore';
import { stompClient, isMatchNotification } from '@/shared/ws/stompClient';
export function SessionEffects() {
  const userId = useAuthStore((s) => s.user?.id);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  useEffect(() => {
    stompClient.connect();
    const off = stompClient.subscribe('/user/queue/match', (value) => {
      if (!isMatchNotification(value)) return;
      void queryClient.invalidateQueries({ queryKey: ['group', value.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['history'] });
      void queryClient.invalidateQueries({ queryKey: ['stats'] });
      useToastStore.getState().notify(value.message);
      if (value.type === 'DISSOLVED') navigate('/waiting', { replace: true });
      else if (value.type === 'PROPOSED' || value.type === 'CONFIRMED')
        navigate(`/groups/${value.groupId}`);
    });
    return () => {
      off();
      stompClient.disconnect();
      queryClient.clear();
    };
  }, [userId, queryClient, navigate]);
  return null;
}
