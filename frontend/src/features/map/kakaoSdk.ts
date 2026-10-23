export interface LatLng {
  lat: number;
  lng: number;
}

export interface KakaoLatLng {
  getLat(): number;
  getLng(): number;
}

export interface KakaoMap {
  setCenter(position: KakaoLatLng): void;
  setBounds(bounds: KakaoBounds): void;
  setLevel(level: number): void;
}

export interface KakaoOverlay {
  setMap(map: KakaoMap | null): void;
}

export interface KakaoBounds {
  extend(position: KakaoLatLng): void;
}

export interface KakaoMapsApi {
  LatLng: new (lat: number, lng: number) => KakaoLatLng;
  Map: new (
    container: HTMLElement,
    options: { center: KakaoLatLng; level: number },
  ) => KakaoMap;
  Marker: new (options: { map: KakaoMap; position: KakaoLatLng; title?: string }) => KakaoOverlay;
  Polyline: new (options: {
    map: KakaoMap;
    path: KakaoLatLng[];
    strokeWeight: number;
    strokeColor: string;
    strokeOpacity: number;
    strokeStyle: string;
  }) => KakaoOverlay;
  CustomOverlay: new (options: {
    map: KakaoMap;
    position: KakaoLatLng;
    content: HTMLElement;
    yAnchor: number;
  }) => KakaoOverlay;
  LatLngBounds: new () => KakaoBounds;
  event: {
    addListener(
      target: KakaoMap,
      eventName: string,
      handler: (event: { latLng: KakaoLatLng }) => void,
    ): void;
    removeListener(
      target: KakaoMap,
      eventName: string,
      handler: (event: { latLng: KakaoLatLng }) => void,
    ): void;
  };
  load(callback: () => void): void;
}

declare global {
  interface Window {
    kakao?: { maps: KakaoMapsApi };
  }
}

let sdkPromise: Promise<KakaoMapsApi> | null = null;

export function loadKakaoMaps(): Promise<KakaoMapsApi> {
  if (sdkPromise) return sdkPromise;

  const appKey = import.meta.env.VITE_KAKAO_JS_KEY;
  if (!appKey) return Promise.reject(new Error('카카오맵 JavaScript 키가 설정되지 않았어요.'));

  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.async = true;
    script.dataset.kakaoMaps = 'true';
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
    script.onerror = () => {
      sdkPromise = null;
      reject(new Error('카카오맵 SDK를 불러오지 못했어요. 키와 허용 도메인을 확인해 주세요.'));
    };
    script.onload = () => {
      const maps = window.kakao?.maps;
      if (!maps) {
        sdkPromise = null;
        reject(new Error('카카오맵 SDK 응답을 확인할 수 없어요.'));
        return;
      }
      maps.load(() => resolve(maps));
    };
    document.head.append(script);
  });

  return sdkPromise;
}
