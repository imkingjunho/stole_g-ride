import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, unwrap } from '@/shared/api/client';
import { Input } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { useKakaoMap } from './useKakaoMap';
import { pickupName } from '@/shared/pickupName';
import type { LatLng } from './kakaoSdk';

export interface DestinationPickerProps {
  origin?: components['schemas']['Hub'];
  value: (LatLng & { name: string }) | null;
  onChange: (value: LatLng & { name: string }) => void;
}

type Place = components['schemas']['Place'];
const CAMPUS_CENTER: LatLng = { lat: 35.17822, lng: 126.90793 };

export function DestinationPicker({ origin, value, onChange }: DestinationPickerProps) {
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const { containerRef, error, isLoading, map, sdk } = useKakaoMap(CAMPUS_CENTER);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const placesQuery = useQuery({
    queryKey: ['places', debouncedSearch],
    enabled: debouncedSearch.length >= 2,
    retry: false,
    queryFn: async () => {
      const response = await api.GET('/api/places/search', {
        params: { query: { q: debouncedSearch } },
      });
      return unwrap(response.data, response.error) ?? [];
    },
  });

  useEffect(() => {
    if (!map || !sdk) return;
    const handleClick = (event: { latLng: { getLat(): number; getLng(): number } }) => {
      onChange({
        name: '지도에서 선택한 위치',
        lat: event.latLng.getLat(),
        lng: event.latLng.getLng(),
      });
      setSearch('');
    };
    sdk.event.addListener(map, 'click', handleClick);
    return () => sdk.event.removeListener(map, 'click', handleClick);
  }, [map, onChange, sdk]);

  useEffect(() => {
    if (!map || !sdk) return;
    const points = [];
    if (origin) points.push({ ...origin, kind: '출발' });
    if (value) points.push({ ...value, kind: '도착' });
    if (!points.length) return;
    const bounds = new sdk.LatLngBounds();
    const overlays = points.map((point) => {
      const position = new sdk.LatLng(point.lat, point.lng);
      bounds.extend(position);
      const badge = document.createElement('span');
      badge.textContent = point.kind;
      badge.className =
        'block rounded-full border-2 border-white px-2 py-1 text-xs font-bold text-white shadow ' +
        (point.kind === '출발' ? 'bg-brand' : 'bg-slate-800');
      return new sdk.CustomOverlay({
        map,
        position,
        content: badge,
        yAnchor: point.kind === '출발' ? 1 : 0,
      });
    });
    if (origin && value && (origin.lat !== value.lat || origin.lng !== value.lng)) {
      map.setBounds(bounds);
    } else {
      map.setLevel(4);
      map.setCenter(new sdk.LatLng(points[0].lat, points[0].lng));
    }
    return () => overlays.forEach((overlay) => overlay.setMap(null));
  }, [map, sdk, origin, value]);

  function selectPlace(place: Place) {
    onChange({ name: place.name, lat: place.lat, lng: place.lng });
    setSearch('');
    setDebouncedSearch('');
  }

  return (
    <div className="space-y-3">
      <Input
        label="목적지 검색"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        placeholder="예: 광주송정역"
        autoComplete="off"
      />
      {debouncedSearch.length >= 2 && (
        <div className="overflow-hidden rounded-control border border-line bg-white">
          {placesQuery.isLoading ? (
            <p className="p-3 text-sm text-slate-600" role="status">
              검색 중…
            </p>
          ) : placesQuery.error ? (
            <QueryState error={placesQuery.error} retry={() => void placesQuery.refetch()} />
          ) : placesQuery.data?.length ? (
            <ul aria-label="장소 검색 결과">
              {placesQuery.data.map((place) => (
                <li key={`${place.name}-${place.lat}-${place.lng}`}>
                  <button
                    type="button"
                    className="min-h-touch w-full border-b border-line px-4 py-3 text-left last:border-0 hover:bg-brand-soft"
                    onClick={() => selectPlace(place)}
                  >
                    <span className="block font-semibold">{place.name}</span>
                    <span className="mt-1 block text-sm text-slate-600">{place.address}</span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="p-3 text-sm text-slate-600">검색 결과가 없어요.</p>
          )}
        </div>
      )}
      {error ? (
        <p role="alert" className="rounded-control bg-red-50 p-3 text-sm text-danger">
          {error.message}
        </p>
      ) : (
        <div
          ref={containerRef}
          aria-label="목적지 지도"
          className="h-64 overflow-hidden rounded-card border border-line bg-slate-100"
          role="application"
        />
      )}
      {isLoading && !error && (
        <p className="text-sm text-slate-600" role="status">
          지도를 불러오고 있어요.
        </p>
      )}
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <dt className="text-slate-600">출발</dt>
        <dd>{origin ? pickupName(origin.name) : '거점을 선택해 주세요'}</dd>
        <dt className="text-slate-600">도착</dt>
        <dd>{value?.name ?? '목적지를 선택해 주세요'}</dd>
      </dl>
      <p className="text-xs text-slate-600">
        출발 핀은 거점의 대략적인 위치예요. 지도를 누르면 도착지를 바꿀 수 있어요.
      </p>
    </div>
  );
}
