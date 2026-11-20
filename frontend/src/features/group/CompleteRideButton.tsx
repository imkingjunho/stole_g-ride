import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, ApiError, unwrap, showError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/stores/authStore';
import { useToastStore } from '@/shared/stores/toastStore';
import { Button, Modal } from '@/shared/ui';
/** 내 탑승을 완료하고 분담액을 확인한다. 다른 동승자의 완료 여부와 별개다. */
export function CompleteRideButton({
  group,
  label = '탑승했어요',
}: {
  group: components['schemas']['GroupDetail'];
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
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
    onError: (error, { groupId, session }) => {
      if (session !== useAuthStore.getState().sessionVersion) return;
      if (
        error instanceof ApiError &&
        ['MATCH_EXPIRED', 'GROUP_NOT_FOUND', 'GROUP_NOT_MEMBER'].includes(error.code)
      )
        void qc.invalidateQueries({ queryKey: ['group', groupId] });
      showError(error);
    },
  });
  if (group.status === 'COMPLETED' || me?.completed)
    return (
      <Link className="link w-full" to={`/groups/${group.groupId}/fare`}>
        탑승 완료 · 정산 금액 보기
      </Link>
    );
  if (group.status !== 'CONFIRMED') return null;
  return (
    <>
      <Button
        fullWidth
        variant="secondary"
        disabled={complete.isPending}
        onClick={() => setOpen(true)}
      >
        {label}
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
            complete.mutate(
              { groupId: group.groupId, session: useAuthStore.getState().sessionVersion },
              {
                onSuccess: (_, { groupId, session }) => {
                  if (
                    session === useAuthStore.getState().sessionVersion &&
                    groupId === group.groupId
                  )
                    navigate(`/groups/${groupId}/fare`, { replace: true });
                },
              },
            )
          }
        >
          탑승 완료 확인
        </Button>
      </Modal>
    </>
  );
}
