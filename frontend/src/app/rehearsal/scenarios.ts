import { isMockMode } from '@/shared/api/config';
import { mockState, resetGroup, createMockRequest, readMockRequest } from '@/shared/mocks/state';
import { mockHistory, mockRideRequest } from '@/shared/mocks/data';
import { useAuthStore } from '@/shared/stores/authStore';

export const rehearsalSteps = [
  { id: 'request', label: '요청 작성', check: '거점과 목적지, 대기·우회 조건을 입력할 수 있다.' },
  { id: 'waiting', label: '대기·취소', check: '남은 시간과 후보 수를 보고 요청을 취소할 수 있다.' },
  { id: 'accept', label: '제안 수락', check: '60초 카운트다운과 수락 후 성사 화면을 확인한다.' },
  { id: 'fare', label: '요금 분담', check: '내 분담액과 절감액, 구간별 내역을 설명할 수 있다.' },
  { id: 'chat', label: '채팅·신고', check: '일반·퀵 메시지와 마스킹, 신고 화면을 확인한다.' },
  { id: 'reject', label: '거절·재대기', check: '거절 후 대기 화면으로 돌아간다.' },
  {
    id: 'recalculate',
    label: '재계산·재동의',
    check: '변경 전후 요금과 다시 수락하는 안내를 확인한다.',
  },
  {
    id: 'estimated',
    label: '추정 경로',
    check: '추정치 배지와 점선을 확인한다. 실제 도로 검증은 별도다.',
  },
  { id: 'error', label: '오류·재시도', check: '오류 안내에서 재시도하면 성사 화면을 복구한다.' },
  { id: 'complete', label: '탑승 완료', check: '완료 확인 후 이력으로 이동하고 채팅이 닫힌다.' },
  {
    id: 'refresh',
    label: '목 세션 갱신',
    check: '무효 목 Access가 갱신되고 내 정보 화면이 열린다.',
  },
] as const;
export type RehearsalStepId = (typeof rehearsalSteps)[number]['id'];

function assertMockRehearsal() {
  if (!import.meta.env.DEV || !isMockMode) throw new Error('목 개발 모드에서만 사용할 수 있어요.');
}
function sampleRequest() {
  return createMockRequest({
    hubId: mockRideRequest.hub.id,
    destName: mockRideRequest.destName,
    destLat: mockRideRequest.destLat,
    destLng: mockRideRequest.destLng,
    departAt: new Date().toISOString(),
    maxWaitMin: 10,
    sameGenderOnly: true,
    maxDetourRatio: 0.2,
  });
}
/** 브라우저의 MSW 데이터만 정리한다. 계정/로그인 상태는 유지한다. */
export function resetMockRehearsal() {
  assertMockRehearsal();
  mockState.request = null;
  resetGroup();
  mockState.history = structuredClone(mockHistory.content);
  mockState.failures.clear();
}
/** 각 단계를 독립적으로 재현한다. 체크 통과 여부를 자동으로 기록하지 않는다. */
export function prepareRehearsalStep(step: RehearsalStepId) {
  assertMockRehearsal();
  mockState.failures.clear();
  if (step === 'refresh') {
    useAuthStore.setState({ accessToken: 'expired-rehearsal' });
    return '/me';
  }
  if (step === 'request') {
    mockState.request = null;
    return '/request';
  }
  if (step === 'waiting') {
    sampleRequest();
    return '/waiting';
  }
  const pending = step === 'accept' || step === 'reject' || step === 'recalculate';
  resetGroup(pending ? 'PENDING' : 'CONFIRMED');
  const groupPath = `/groups/${mockState.group.groupId}`;
  const request = sampleRequest();
  request.status = pending ? 'MATCHED' : 'CONFIRMED';
  request.groupId = mockState.group.groupId;
  if (step === 'recalculate') {
    const me = mockState.group.members.find((member) => member.isMe)!;
    // 원래 요금으로 화면을 연 다음 별도 버튼으로 변경 알림을 보내게 한다.
    me.accepted = false;
  }
  if (step === 'estimated') mockState.group.estimated = true;
  if (step === 'error') mockState.failures.set(`GET /api/groups/${mockState.group.groupId}`, 2);
  if (step === 'fare') return `${groupPath}/fare`;
  if (step === 'chat') return `${groupPath}/chat`;
  // 만료 시각은 매번 현재 시각으로 만든다. 예전 샘플 날짜를 대기 타이머로 사용하지 않는다.
  readMockRequest();
  return groupPath;
}
