import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, unwrap, showError } from '@/shared/api/client';
import { useToastStore } from '@/shared/stores/toastStore';
import { Button, Modal } from '@/shared/ui';
const reasons: Record<components['schemas']['ReportRequest']['reason'], string> = {
  NO_SHOW: '약속 장소에 나오지 않음',
  RUDE: '불쾌한 언행',
  UNSAFE: '안전 문제',
  PAYMENT: '요금 분담 문제',
  OTHER: '기타',
};
export function ReportButton({ group }: { group: components['schemas']['GroupDetail'] }) {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState('');
  const [reason, setReason] = useState<components['schemas']['ReportRequest']['reason']>('NO_SHOW');
  const [detail, setDetail] = useState('');
  const report = useMutation({
    mutationFn: async () => {
      const r = await api.POST('/api/reports', {
        body: {
          groupId: group.groupId,
          reportedUserId: Number(target),
          reason,
          detail: detail.trim() || null,
        },
      });
      return unwrap(r.data, r.error);
    },
    onSuccess: () => {
      setOpen(false);
      setDetail('');
      useToastStore.getState().notify('신고가 접수됐어요.', 'success');
    },
    onError: showError,
  });
  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>
        동승자 신고
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="동승자 신고"
        description="같은 동승 그룹의 이용자만 신고할 수 있어요."
      >
        <form
          className="space-y-4"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            report.mutate();
          }}
        >
          <label className="block text-sm">
            신고할 동승자
            <select
              aria-label="신고할 동승자"
              required
              className="field mt-2"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
            >
              <option value="">선택해 주세요</option>
              {group.members
                .filter((m) => !m.isMe)
                .map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.nickname}
                  </option>
                ))}
            </select>
          </label>
          <label className="block text-sm">
            사유
            <select
              aria-label="신고 사유"
              className="field mt-2"
              value={reason}
              onChange={(e) => setReason(e.target.value as typeof reason)}
            >
              {Object.entries(reasons).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            상세 내용 (선택)
            <textarea
              className="field mt-2"
              maxLength={500}
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
            />
          </label>
          <Button fullWidth type="submit" variant="danger" loading={report.isPending}>
            신고 제출
          </Button>
        </form>
      </Modal>
    </>
  );
}
