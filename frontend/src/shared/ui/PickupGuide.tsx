import type { components } from '@/generated/api';

// 현장 확인 전 상호·출구 번호를 추측하지 않는다. 운영팀 확인 내용은 docs/pickup-guide.md 참고.
const guides: Record<string, { spot: string; detail: string }> = {
  '전남대 정문': {
    spot: '정문 바깥쪽 입구 옆 보행 공간',
    detail:
      '정문을 등지고 나왔을 때 입구 바로 옆에서 먼저 모여 주세요. 차도·횡단보도를 막지 않는 쪽을 채팅으로 알려 주세요.',
  },
  '전남대 후문': {
    spot: '후문 버스정류장 주변의 넓은 보행 공간',
    detail:
      '정류장 이름과 도로 어느 쪽인지 확인해 채팅으로 알려 주세요. 버스 승하차 구역에서는 기다리지 말고, 함께 모인 뒤 택시 탑승 장소로 이동해 주세요.',
  },
  '예대 삼거리': {
    spot: '예술대학으로 들어가는 입구 쪽 보행 공간',
    detail:
      '삼거리의 어느 모퉁이인지 입구 표지나 주변 건물을 기준으로 채팅에서 확인해 주세요. 교차로 모서리와 차도는 피해 주세요.',
  },
  경신여고: {
    spot: '학교 정문 바깥쪽 보행 공간',
    detail:
      '정문을 기준으로 기다리는 쪽을 채팅으로 알려 주세요. 학교 진입로를 비워 두고 함께 모인 뒤 택시 탑승 장소를 정해 주세요.',
  },
  유스퀘어: {
    spot: '터미널 택시승강장 안내 표지 주변',
    detail:
      '터미널에서 택시승강장 방향 안내를 따라 이동해 주세요. 대기 줄 진입 전 보행 공간에서 서로 확인하고 함께 줄을 서 주세요.',
  },
  광주송정역: {
    spot: '지하철 광주송정역 2번 출구 앞 전용승차대 주변',
    detail:
      '현장 택시 전용승차대 표지를 확인한 뒤 대기 줄 바깥 보행 공간에서 먼저 만나세요. 지하철 3번 출구 앞 하차 전용구역과 혼동하지 마세요.',
  },
};
export function PickupGuide({ hub }: { hub: components['schemas']['Hub'] }) {
  const guide = guides[hub.name === '전남대 예대 삼거리' ? '예대 삼거리' : hub.name];
  return (
    <aside
      className="rounded-control border border-line bg-brand-soft p-4"
      aria-label="만남 장소 안내"
    >
      <p className="text-xs font-semibold text-brand">추천 만남 장소 · {hub.name}</p>
      <p className="mt-2 font-bold">{guide?.spot ?? '거점 입구 주변의 보행 공간'}</p>
      <p className="mt-2 text-sm leading-relaxed text-slate-700">
        {guide?.detail ??
          '입구 표지나 주변 가게 이름을 채팅으로 공유해 정확히 만날 곳을 정해 주세요.'}
      </p>
      <p className="mt-2 text-xs text-slate-600">
        현장 표지와 채팅으로 최종 위치를 확인해 주세요. 지도 핀은 거점의 대략적인 위치예요.
      </p>
    </aside>
  );
}
