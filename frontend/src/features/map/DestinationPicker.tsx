import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, unwrap } from '@/shared/api/client';
import { Input } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { useKakaoMap } from './useKakaoMap';
import type { KakaoOverlay, LatLng } from './kakaoSdk';

export interface DestinationPickerProps {
  value: (LatLng & { name: string }) | null;
  onChange: (value: LatLng & { name: string }) => void;
}

type Place = components['schemas']['Place'];
const CAMPUS_CENTER: LatLng = { lat: 35.17822, lng: 126.90793 };

export function DestinationPicker({ value, onChange }: DestinationPickerProps) {
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

  const selectedPosition = useMemo(
    () => (sdk && value ? new sdk.LatLng(value.lat, value.lng) : null),
    [sdk, value],
  );

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
    if (!map || !sdk || !selectedPosition) return;
    const marker: KakaoOverlay = new sdk.Marker({
      map,
      position: selectedPosition,
      title: value?.name,
    });
    map.setCenter(selectedPosition);
    return () => marker.setMap(null);
  }, [map, sdk, selectedPosition, value?.name]);

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
      {value && (
        <p className="text-sm text-slate-600">
          선택한 목적지: <strong className="text-ink">{value.name}</strong>
        </p>
      )}
      <p className="text-xs text-slate-600">지도에서 위치를 눌러 목적지를 조정할 수 있어요.</p>
    </div>
  );
}
