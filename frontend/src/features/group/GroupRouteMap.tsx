import { useMemo } from 'react';
import type { components } from '@/generated/api';
import { RouteMap, type Stop } from '@/features/map/RouteMap';

type Group = components['schemas']['GroupDetail'];
function validPosition(lat: number | undefined, lng: number | undefined) {
  return (
    Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat!) <= 90 && Math.abs(lng!) <= 180
  );
}

/** 서버 좌표만 사용한다. 목적지 좌표가 없으면 임의의 경로 끝점으로 대체하지 않는다. */
export function GroupRouteMap({ group }: { group: Group }) {
  const { sections, stops, missing } = useMemo(() => {
    const sections = group.route.sections.map((section) => ({
      path: section.path.filter((point) => validPosition(point.lat, point.lng)),
    }));
    const stops: Stop[] = [];
    if (validPosition(group.hub.lat, group.hub.lng))
      stops.push({ order: 1, kind: 'PICKUP', position: group.hub, label: group.hub.name });
    let missing = 0;
    for (const member of [...group.members].sort((a, b) => a.dropoffOrder - b.dropoffOrder)) {
      if (!validPosition(member.destLat, member.destLng)) {
        missing++;
        continue;
      }
      stops.push({
        order: member.dropoffOrder,
        kind: 'DROPOFF',
        position: { lat: member.destLat!, lng: member.destLng! },
        label: `${member.nickname}${member.isMe ? ' (나)' : ''} · ${member.destName}`,
      });
    }
    return { sections, stops, missing };
  }, [group]);
  return (
    <div className="my-4 space-y-2">
      {sections.some((section) => section.path.length > 1) || stops.length ? (
        <RouteMap sections={sections} stops={stops} />
      ) : (
        <p role="status" className="rounded-control bg-canvas p-4 text-sm">
          경로 좌표를 준비하고 있어요.
        </p>
      )}
      {missing > 0 && (
        <p className="text-sm text-slate-600">
          일부 하차 위치가 아직 없어 지도에 표시되지 않아요. 아래 하차 순서를 확인해 주세요.
        </p>
      )}
      {group.estimated && (
        <p className="text-sm text-slate-600">
          추정 경로예요. 실제 도로와 이동 시간은 다를 수 있어요.
        </p>
      )}
    </div>
  );
}
