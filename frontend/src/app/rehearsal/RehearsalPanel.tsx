import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@/shared/ui';
import { showError } from '@/shared/api/client';
import { isMockMode } from '@/shared/api/config';
import { useToastStore } from '@/shared/stores/toastStore';
import { mockState, notifyMatch } from '@/shared/mocks/state';
import {
  rehearsalSteps,
  prepareRehearsalStep,
  resetMockRehearsal,
  type RehearsalStepId,
} from './scenarios';
import { useRehearsalStore } from './rehearsalStore';

export function RehearsalPanel() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const { checked, mark, reset, start } = useRehearsalStore();
  if (!import.meta.env.DEV || !isMockMode) return null;
  const count = rehearsalSteps.filter((step) => checked[step.id]).length;
  async function run(step?: RehearsalStepId) {
    setBusy(true);
    try {
      await qc.cancelQueries();
      useToastStore.setState({ messages: [] });
      if (!step) {
        resetMockRehearsal();
        reset();
        qc.clear();
      } else {
        const path = prepareRehearsalStep(step);
        qc.clear();
        start(step);
        navigate(path);
      }
    } catch (error) {
      showError(error);
    } finally {
      setBusy(false);
    }
  }
  function download() {
    const body = {
      scope: 'MOCK_REHEARSAL',
      actualServerVerified: false,
      createdAt: new Date().toISOString(),
      results: rehearsalSteps.map((step) => ({
        id: step.id,
        label: step.label,
        checked: !!checked[step.id],
      })),
    };
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(body, null, 2)], { type: 'application/json' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = 'we-meet-rehearsal.json';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
  return (
    <Card className="space-y-4">
      <h2 className="text-xl font-bold">발표 시연 리허설</h2>
      <p className="text-sm text-slate-600">
        각 버튼은 목 데이터를 준비하고 해당 화면을 열어요. 확인한 항목만 직접 체크해 주세요. 실제
        베타 테스트 결과는 아니에요.
      </p>
      <p role="status" className="font-semibold">
        확인한 시연 항목 {count}/{rehearsalSteps.length}
      </p>
      <Button fullWidth variant="secondary" disabled={busy} onClick={() => void run()}>
        목 리허설 새로 시작
      </Button>
      <p className="text-xs text-slate-600">
        새로 시작하면 목 요청·대화·그룹·이력과 체크 기록을 초기화해요. 로그인은 유지돼요.
      </p>
      <ol className="space-y-4">
        {rehearsalSteps.map((step, index) => (
          <li key={step.id} className="space-y-2 border-t border-line pt-3">
            <label className="flex min-h-touch items-center gap-3 font-semibold">
              <input
                type="checkbox"
                checked={!!checked[step.id]}
                onChange={(event) => mark(step.id, event.target.checked)}
              />
              {index + 1}. {step.label} 확인
            </label>
            <p className="text-sm text-slate-600">{step.check}</p>
            <Button variant="secondary" fullWidth disabled={busy} onClick={() => void run(step.id)}>
              {step.label} 연습
            </Button>
          </li>
        ))}
      </ol>
      <Button fullWidth variant="secondary" onClick={download}>
        리허설 기록 다운로드
      </Button>
    </Card>
  );
}
export function RehearsalStatus() {
  const { active } = useRehearsalStore();
  if (!import.meta.env.DEV || !isMockMode || !active) return null;
  const step = rehearsalSteps.find((item) => item.id === active);
  return (
    <aside aria-label="목 시연 도우미" className="space-y-2 bg-brand-soft px-page py-3 text-sm">
      <p className="font-semibold">목 시연 연습: {step?.label}</p>
      <Link className="link px-0" to="/dev/demo">
        시연 체크리스트로 돌아가기
      </Link>
      {active === 'recalculate' && (
        <Button
          variant="secondary"
          fullWidth
          onClick={() => {
            if (mockState.group.status !== 'PENDING') return;
            const me = mockState.group.members.find((member) => member.isMe)!;
            // 정산 계산이 아닌 목 표시용 변경 예시다.
            me.shareAmount = 9600;
            me.savingAmount = me.soloFare - me.shareAmount;
            delete me.breakdown;
            delete me.roundingAdjustment;
            mockState.group.totalFare = mockState.group.members.reduce(
              (sum, member) => sum + member.shareAmount,
              0,
            );
            mockState.group.members.forEach((member) => {
              member.accepted = false;
            });
            mockState.group.acceptDeadline = new Date(Date.now() + 60000).toISOString();
            notifyMatch('RECALCULATED', '목 시연: 경로와 분담액이 변경됐어요.');
          }}
        >
          목 재계산 알림 보내기
        </Button>
      )}
    </aside>
  );
}
