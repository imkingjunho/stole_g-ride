import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, unwrap, showError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import { useToastStore } from '@/shared/stores/toastStore';
import { Button, Modal } from '@/shared/ui';
/** 채팅을 떠나지 않고 내 탑승을 완료한다. 다른 동승자의 완료 여부와 별개다. */
export function CompleteRideButton({ group }: { group: components['schemas']['GroupDetail'] }) {
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const me = group.members.find((m) => m.isMe);
  const complete = useMutation({
    mutationFn: async ({ groupId }: { groupId: number; session: number }) => {
      const r = await api.POST('/api/groups/{groupId}/complete', { params: { path: { groupId } } });
      unwrap(r.data, r.error);
    },
    onSuccess: (_, { groupId, session }) => {
      if (session !== useAuthStore.getState().sessionVersion) return;
      void qc.invalidateQueries({ queryKey: ['group', groupId] });
      void qc.invalidateQueries({ queryKey: ['myRequest'] });
      void qc.invalidateQueries({ queryKey: ['history'] });
      void qc.invalidateQueries({ queryKey: ['stats'] });
      setOpen(false);
      useToastStore.getState().notify('탑승 완료했어요. 채팅은 계속 이용할 수 있어요.', 'success');
    },
    onError: (error, { session }) => {
      if (session === useAuthStore.getState().sessionVersion) showError(error);
    },
  });
  if (group.status === 'COMPLETED')
    return (
      <p role="status" className="rounded-control bg-brand-soft p-3 text-sm text-brand">
        탑승 완료 · 함께한 이동에 기록했어요.
      </p>
    );
  if (group.status !== 'CONFIRMED') return null;
  return (
    <>
      <Button
        fullWidth
        variant="secondary"
        disabled={me?.completed || complete.isPending}
        onClick={() => setOpen(true)}
      >
        {me?.completed ? '탑승 완료했어요' : '탑승했어요'}
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="탑승 완료를 기록할까요?"
        description="함께한 이동에 저장돼요. 채팅은 매칭 확정 후 3시간까지 계속할 수 있어요."
      >
        <Button
          fullWidth
          loading={complete.isPending}
          onClick={() =>
            complete.mutate({
              groupId: group.groupId,
              session: useAuthStore.getState().sessionVersion,
            })
          }
        >
          탑승 완료 확인
        </Button>
      </Modal>
    </>
  );
}
