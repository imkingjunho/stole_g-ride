import { pickupName } from '@/shared/pickupName';
import type { components } from '@/generated/api';

export function PickupGuide({ hub }: { hub: components['schemas']['Hub'] }) {
  const taxiStand = hub.name === '유스퀘어' || hub.name === '광주송정역';
  return (
    <aside
      className="rounded-control border border-line bg-brand-soft p-4"
      aria-label="만남 장소 안내"
    >
      <p className="text-xs font-semibold text-brand">추천 만남 장소 · {hub.name}</p>
      <p className="mt-2 font-bold">{pickupName(hub.name)}</p>
      <p className="mt-2 text-sm leading-relaxed text-slate-700">
        {taxiStand
          ? '택시정류장 안내 표지 앞에서 만나 함께 줄을 서 주세요.'
          : '입구 옆 보행 공간에서 만나세요. 먼저 도착하면 채팅으로 기다리는 쪽을 알려 주세요.'}
      </p>
      <p className="mt-2 text-xs text-slate-600">
        현장 표지와 채팅으로 최종 위치를 확인해 주세요. 지도 핀은 거점의 대략적인 위치예요.
      </p>
    </aside>
  );
}
