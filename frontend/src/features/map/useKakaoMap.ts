import { useEffect, useRef, useState } from 'react';
import { loadKakaoMaps, type KakaoMap, type KakaoMapsApi, type LatLng } from './kakaoSdk';

const DEFAULT_CENTER: LatLng = { lat: 35.17822, lng: 126.90793 };

export function useKakaoMap(center: LatLng = DEFAULT_CENTER) {
  const containerRef = useRef<HTMLDivElement>(null);
  const initialCenter = useRef(center);
  const [sdk, setSdk] = useState<KakaoMapsApi | null>(null);
  const [map, setMap] = useState<KakaoMap | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;
    void loadKakaoMaps()
      .then((loadedSdk) => {
        if (active) setSdk(loadedSdk);
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason : new Error('지도를 불러오지 못했어요.'));
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!sdk || !container) return;
    const instance = new sdk.Map(container, {
      center: new sdk.LatLng(initialCenter.current.lat, initialCenter.current.lng),
      level: 5,
    });
    instance.setZoomable(true);
    setMap(instance);
    return () => setMap(null);
  }, [sdk]);

  useEffect(() => {
    if (sdk && map) map.setCenter(new sdk.LatLng(center.lat, center.lng));
  }, [center.lat, center.lng, map, sdk]);

  return { containerRef, error, isLoading: !error && !map, map, sdk };
}
