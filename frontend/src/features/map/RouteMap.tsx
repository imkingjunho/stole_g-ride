import { useEffect, useRef } from 'react';
import { useKakaoMap } from './useKakaoMap';
import type { KakaoOverlay, LatLng } from './kakaoSdk';

export interface Stop {
  order: number;
  kind: 'PICKUP' | 'DROPOFF';
  position: LatLng;
  label: string;
}

export interface RouteMapProps {
  sections: { path: LatLng[] }[];
  stops: Stop[];
  className?: string;
}

const DEFAULT_CENTER: LatLng = { lat: 35.17822, lng: 126.90793 };

export function RouteMap({ sections, stops, className = '' }: RouteMapProps) {
  const { containerRef, error, isLoading, map, sdk } = useKakaoMap(DEFAULT_CENTER);
  const overlays = useRef<KakaoOverlay[]>([]);

  useEffect(() => {
    if (!map || !sdk) return;
    overlays.current.forEach((overlay) => overlay.setMap(null));
    overlays.current = [];
    const bounds = new sdk.LatLngBounds();
    let pointCount = 0;

    sections.forEach((section) => {
      const path = section.path.map((point) => new sdk.LatLng(point.lat, point.lng));
      path.forEach((point) => {
        bounds.extend(point);
        pointCount += 1;
      });
      if (path.length > 1) {
        overlays.current.push(new sdk.Polyline({
          map,
          path,
          strokeWeight: 5,
          strokeColor: '#1769d4',
          strokeOpacity: 0.85,
          strokeStyle: 'solid',
        }));
      }
    });

    stops.forEach((stop) => {
      const position = new sdk.LatLng(stop.position.lat, stop.position.lng);
      bounds.extend(position);
      pointCount += 1;
      const badge = document.createElement('div');
      const sequence = document.createElement('span');
      sequence.textContent = `${stop.order} ${stop.kind === 'PICKUP' ? '탑승' : '하차'}`;
      sequence.className = 'font-bold';
      const label = document.createElement('span');
      label.textContent = stop.label;
      label.className = 'max-w-32 truncate';
      badge.append(sequence, label);
      badge.setAttribute(
        'aria-label',
        `${stop.order}번 ${stop.kind === 'PICKUP' ? '탑승' : '하차'}: ${stop.label}`,
      );
      badge.setAttribute('role', 'img');
      badge.className =
        'flex max-w-48 items-center gap-1 rounded-full border-2 border-white bg-brand px-3 py-1.5 text-xs text-white shadow';
      overlays.current.push(new sdk.CustomOverlay({ map, position, content: badge, yAnchor: 1 }));
    });

    if (pointCount > 0) map.setBounds(bounds);
    return () => {
      overlays.current.forEach((overlay) => overlay.setMap(null));
      overlays.current = [];
    };
  }, [map, sdk, sections, stops]);

  if (error) {
    return (
      <p role="alert" className={`rounded-control bg-red-50 p-3 text-sm text-danger ${className}`}>
        {error.message}
      </p>
    );
  }

  return (
    <div className={className}>
      <div
        ref={containerRef}
        aria-label="합승 경로 지도"
        className="h-64 overflow-hidden rounded-card border border-line bg-slate-100"
        role="application"
      />
      {isLoading && (
        <p role="status" className="mt-2 text-sm text-slate-600">
          지도를 불러오고 있어요.
        </p>
      )}
      <ol className="mt-3 flex flex-wrap gap-2" aria-label="경로 정차 순서">
        {stops.map((stop) => (
          <li key={`${stop.kind}-${stop.order}`} className="text-sm">
            <span className="font-semibold">{stop.order}. </span>
            {stop.label}
          </li>
        ))}
      </ol>
    </div>
  );
}
