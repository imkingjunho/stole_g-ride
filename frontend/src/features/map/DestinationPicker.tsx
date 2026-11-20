import './destination.css';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { components } from '@/generated/api';
import { api, unwrap } from '@/shared/api/client';
import { Button } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { pickupName } from '@/shared/pickupName';
import { useKakaoMap } from './useKakaoMap';
import type { KakaoLatLng, LatLng } from './kakaoSdk';

type Point = LatLng & { name: string };
interface Props {
  origin: components['schemas']['Hub'];
  value: Point | null;
  recent: Point[];
  onConfirm: (point: Point) => void;
}
export function DestinationPicker({ origin, value, recent, onConfirm }: Props) {
  const [pin, setPin] = useState(value);
  const [anchor, setAnchor] = useState(value);
  const [address, setAddress] = useState('');
  const { containerRef, error, isLoading, map, sdk } = useKakaoMap(value ?? origin);
  const [text, setText] = useState('');
  const [searching, setSearching] = useState(false);
  const [query, debounce] = useState('');
  useEffect(() => {
    const timer = window.setTimeout(() => debounce(text.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [text]);
  const results = useQuery({
    queryKey: ['places', query],
    enabled: searching && query.length >= 2,
    retry: false,
    queryFn: async ({ signal }) => {
      const r = await api.GET('/api/places/search', { params: { query: { q: query } }, signal });
      return unwrap(r.data, r.error) ?? [];
    },
  });
  function choose(point: Point, detail = '') {
    setPin(point);
    setAnchor({ ...point });
    setAddress(detail);
    setSearching(false);
    setText('');
    debounce('');
  }
  useEffect(() => {
    if (!map || !sdk) return;
    map.setLevel(3);
    map.setCenter(new sdk.LatLng(anchor?.lat ?? origin.lat, anchor?.lng ?? origin.lng));
  }, [anchor, origin.lat, origin.lng, map, sdk]);
  useEffect(() => {
    if (!map || !sdk) return;
    const pick = ({ latLng }: { latLng: KakaoLatLng }) => {
      const lat = latLng.getLat(),
        lng = latLng.getLng();
      // 기존 장소 주변에서 조정했을 때만 그 장소명을 유지한다.
      const metres = anchor
        ? Math.hypot(
            (lat - anchor.lat) * 111320,
            (lng - anchor.lng) * 111320 * Math.cos((lat * Math.PI) / 180),
          )
        : Infinity;
      const name =
        anchor && metres < 500 && anchor.name !== '지도에서 지정한 위치'
          ? anchor.name.replace(/ 인근$/, '') + ' 인근'
          : '지도에서 지정한 위치';
      setPin({ lat, lng, name });
      setAddress('지도에서 지정한 하차 지점');
      setSearching(false);
      setText('');
      debounce('');
    };
    sdk.event.addListener(map, 'click', pick);
    return () => sdk.event.removeListener(map, 'click', pick);
  }, [map, sdk, anchor]);
  useEffect(() => {
    if (!map || !sdk) return;
    const overlays = [{ ...origin, tag: '출발' }, ...(pin ? [{ ...pin, tag: '도착' }] : [])].map(
      (point) => {
        const badge = document.createElement('span');
        badge.className = 'trip-pin ' + (point.tag === '도착' ? 'arrival' : '');
        badge.textContent = point.tag;
        badge.setAttribute('role', 'img');
        badge.setAttribute(
          'aria-label',
          point.tag + ' · ' + (point.tag === '출발' ? pickupName(origin.name) : point.name),
        );
        return new sdk.CustomOverlay({
          map,
          position: new sdk.LatLng(point.lat, point.lng),
          content: badge,
          yAnchor: 1,
        });
      },
    );
    return () => overlays.forEach((o) => o.setMap(null));
  }, [map, sdk, origin, pin]);

  return (
    <div className="destination">
      <div className="dest-surface">
        <div
          ref={containerRef}
          className="dest-map"
          aria-label="지도에서 하차 위치 선택"
          role="region"
        />
        <div className="dest-search">
          <div className="dest-input">
            <svg
              aria-hidden="true"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <circle cx="10" cy="10" r="6.5" />
              <path d="m15 15 6 6" />
            </svg>
            <input
              aria-label="목적지 검색"
              value={text}
              placeholder={pin?.name ?? '장소명 또는 주소 검색'}
              autoComplete="off"
              onFocus={() => setSearching(true)}
              onChange={(e) => {
                setText(e.target.value);
                setSearching(true);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  e.currentTarget.blur();
                  setSearching(false);
                }
              }}
            />
            {searching && (
              <button
                aria-label="검색 닫기"
                onClick={() => {
                  setSearching(false);
                  setText('');
                  debounce('');
                }}
              >
                ×
              </button>
            )}
          </div>
          {searching && (
            <div className="dest-results" role="region" aria-label="장소 검색 결과">
              {text.trim().length < 2 ? (
                <>
                  {recent.length > 0 && (
                    <>
                      <h3>최근 목적지</h3>
                      {recent.map((p) => (
                        <button key={p.lat + ',' + p.lng} onClick={() => choose(p)}>
                          {p.name}
                        </button>
                      ))}
                    </>
                  )}
                  <p>장소명이나 주소를 두 글자 이상 입력해 주세요.</p>
                </>
              ) : query !== text.trim() || results.isLoading ? (
                <p role="status">검색 중…</p>
              ) : results.error ? (
                <QueryState error={results.error} retry={() => void results.refetch()} />
              ) : results.data?.length ? (
                results.data.map((p) => (
                  <button
                    key={p.name + p.lat + p.lng}
                    onClick={() =>
                      choose({ name: p.name, lat: p.lat, lng: p.lng }, p.address ?? '')
                    }
                  >
                    <strong>{p.name}</strong>
                    <small>{p.address}</small>
                  </button>
                ))
              ) : (
                <p>검색 결과가 없어요.</p>
              )}
            </div>
          )}
        </div>
        {(error || isLoading) && (
          <p className="dest-map-state" role="status">
            {error
              ? '지도를 표시할 수 없어요. 검색으로 목적지를 선택해 주세요.'
              : '지도를 불러오는 중이에요.'}
          </p>
        )}
      </div>
      <div className="dest-confirm" aria-live="polite">
        <p className="text-xs text-slate-600">출발 · {pickupName(origin.name)}</p>
        <h2>{pin?.name ?? '어디까지 갈까요?'}</h2>
        {address && <p>{address}</p>}
        <p>
          {pin
            ? error
              ? '선택한 목적지를 확인해 주세요.'
              : '지도를 눌러 내릴 위치를 조정하세요.'
            : '장소를 검색하거나 지도에서 내릴 곳을 눌러 주세요.'}
        </p>
        {pin && (
          <Button fullWidth disabled={searching} onClick={() => onConfirm(pin)}>
            여기로 도착
          </Button>
        )}
      </div>
    </div>
  );
}
