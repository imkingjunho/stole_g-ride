import './request.css';
import { pickupName, resolvePickup } from '@/shared/pickupName';
import { RouteMap } from '@/features/map/RouteMap';
import { MatchConditions } from './MatchConditions';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import type { components } from '@/generated/api';
import { api, ApiError, showError, unwrap } from '@/shared/api/client';
import { Button } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { DestinationPicker } from '@/features/map/DestinationPicker';
import {
  isDestinationList,
  MAX_RECENT_DESTINATIONS,
  RECENT_DESTINATIONS_KEY,
  sortHubsByDistance,
  type Destination,
} from './requestPreferences';

type Hub = components['schemas']['Hub'];
type CreateRideRequest = components['schemas']['CreateRideRequest'];
type Ride = components['schemas']['RideRequestDetail'];

function blocksNewRequest(ride?: Ride | null) {
  return (
    ride?.status === 'MATCHED' ||
    ride?.status === 'CONFIRMED' ||
    (ride?.status === 'WAITING' && ride.remainingSeconds > 0)
  );
}

type Step = 'origin' | 'destination' | 'conditions' | 'review';
const stepLabels = {
  origin: '출발 거점',
  destination: '목적지',
  conditions: '매칭 조건',
  review: '요청 확인',
};

function localDateTime(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
}

export function RequestPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>('origin');
  const [editing, setEditing] = useState(false);
  const [hubId, setHubId] = useState<number | null>(null);
  const [destination, setDestination] = useState<Destination | null>(null);
  const [recentDestinations, setRecentDestinations] = useState<Destination[]>([]);
  const [recentDestinationsError, setRecentDestinationsError] = useState<string | null>(null);
  const [currentLocation, setCurrentLocation] = useState<Destination | null>(null);
  const [isLocating, setIsLocating] = useState(false);
  const [locationMessage, setLocationMessage] = useState<string | null>(null);
  const [maxWaitMin, setMaxWaitMin] = useState<CreateRideRequest['maxWaitMin']>(10);
  const [sameGenderOnly, setSameGenderOnly] = useState(false);
  const [maxDetourRatio, setMaxDetourRatio] = useState<CreateRideRequest['maxDetourRatio']>(0.2);

  const [preferredSize, setPreferredSize] = useState<2 | 3>(2);
  const preview = useQuery({
    queryKey: ['requestPreview', hubId, destination?.lat, destination?.lng],
    enabled: !!hubId && !!destination && (step === 'conditions' || step === 'review'),
    staleTime: 60_000,
    retry: false,
    queryFn: async ({ signal }) => {
      if (!hubId || !destination) throw new Error('출발지와 도착지를 선택해 주세요.');
      const r = await api.GET('/api/requests/preview', {
        params: { query: { hubId, destLat: destination.lat, destLng: destination.lng } },
        signal,
      });
      if (r.response.status === 404 || r.response.status === 405) return null;
      return unwrap(r.data, r.error);
    },
  });
  const sizeOptions = preview.data?.groupSizes;
  const hasRoad = preview.data?.sections.some((section) => section.path.length >= 2);

  const hubsQuery = useQuery({
    queryKey: ['hubs'],
    retry: false,
    queryFn: async () => {
      const response = await api.GET('/api/hubs');
      return (unwrap(response.data, response.error) ?? []).map(resolvePickup);
    },
  });
  const currentRequestQuery = useQuery<Ride | null>({
    queryKey: ['myRequest'],
    retry: false,
    refetchInterval: (query) => (blocksNewRequest(query.state.data) ? 5000 : false),
    queryFn: async ({ signal }) => {
      const response = await api.GET('/api/requests/me', { signal });
      return unwrap(response.data, response.error);
    },
  });
  const current = currentRequestQuery.data;
  const activeRequest = blocksNewRequest(current) ? current : null;

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [step]);

  useEffect(() => {
    let stored: string | null;
    try {
      stored = window.localStorage.getItem(RECENT_DESTINATIONS_KEY);
    } catch (error) {
      if (!(error instanceof DOMException)) throw error;
      setRecentDestinationsError('최근 목적지를 불러오지 못했어요.');
      return;
    }
    if (stored === null) return;
    let parsed: unknown;
    try {
      parsed = JSON.parse(stored);
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
      setRecentDestinationsError('최근 목적지를 불러오지 못했어요.');
      return;
    }
    if (!isDestinationList(parsed)) {
      setRecentDestinationsError('최근 목적지를 불러오지 못했어요.');
      return;
    }
    setRecentDestinations(parsed.slice(0, MAX_RECENT_DESTINATIONS));
  }, []);

  function handleDestinationChange(next: Destination) {
    setDestination(next);
    setStep(editing ? 'review' : 'conditions');
    setEditing(false);
    const nextRecentDestinations = [
      next,
      ...recentDestinations.filter((recent) => recent.lat !== next.lat || recent.lng !== next.lng),
    ].slice(0, MAX_RECENT_DESTINATIONS);
    setRecentDestinations(nextRecentDestinations);
    try {
      window.localStorage.setItem(RECENT_DESTINATIONS_KEY, JSON.stringify(nextRecentDestinations));
      setRecentDestinationsError(null);
    } catch (error) {
      if (!(error instanceof DOMException)) throw error;
      setRecentDestinationsError('최근 목적지를 저장하지 못했어요.');
    }
  }

  function requestCurrentLocation() {
    if (!navigator.geolocation) {
      setLocationMessage('이 브라우저에서는 현재 위치를 사용할 수 없어요.');
      return;
    }
    setIsLocating(true);
    setLocationMessage(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setCurrentLocation({
          name: '현재 위치',
          lat: coords.latitude,
          lng: coords.longitude,
        });
        setIsLocating(false);
        setLocationMessage('현재 위치에서 가까운 거점순으로 표시하고 있어요.');
      },
      (error) => {
        setCurrentLocation(null);
        setIsLocating(false);
        setLocationMessage(
          error.code === error.PERMISSION_DENIED
            ? '위치 권한이 허용되지 않아 기본 순서로 표시해요.'
            : '현재 위치를 확인하지 못해 기본 순서로 표시해요.',
        );
      },
      { enableHighAccuracy: false, maximumAge: 60_000, timeout: 10_000 },
    );
  }

  const createRequest = useMutation({
    mutationFn: async (hub: Hub) => {
      if (!destination) throw new Error('목적지를 선택해 주세요.');
      const body = {
        hubId: hub.id,
        ...(sizeOptions?.includes(preferredSize) && { preferredSize }),
        destName: destination.name,
        destLat: destination.lat,
        destLng: destination.lng,
        departAt: localDateTime(),
        maxWaitMin,
        sameGenderOnly,
        maxDetourRatio,
      } satisfies CreateRideRequest;
      const response = await api.POST('/api/requests', { body });
      return unwrap(response.data, response.error);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['myRequest'] });
      navigate('/waiting');
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'ALREADY_IN_QUEUE')
        void queryClient.invalidateQueries({ queryKey: ['myRequest'] });
      showError(error);
    },
  });

  const selectedHub = hubsQuery.data?.find((hub) => hub.id === hubId);
  const sortedHubs = currentLocation
    ? sortHubsByDistance(hubsQuery.data ?? [], currentLocation)
    : (hubsQuery.data ?? []);

  if (hubsQuery.isLoading || currentRequestQuery.isLoading) {
    return <QueryState loading />;
  }
  if (hubsQuery.error || currentRequestQuery.error) {
    return (
      <section className="page">
        <QueryState
          error={hubsQuery.error ?? currentRequestQuery.error}
          retry={() => {
            void hubsQuery.refetch();
            void currentRequestQuery.refetch();
          }}
        />
      </section>
    );
  }
  if (!hubsQuery.data?.length) {
    return <QueryState empty="선택할 수 있는 출발 거점이 없어요." />;
  }

  const index = step === 'origin' ? 1 : step === 'destination' ? 2 : 3;
  function edit(target: Step) {
    setEditing(true);
    setStep(target);
  }
  function back() {
    if (editing) {
      setEditing(false);
      setStep('review');
      return;
    }
    setStep(step === 'review' ? 'conditions' : step === 'conditions' ? 'destination' : 'origin');
  }
  const route =
    selectedHub && destination ? (
      <div className="trip-endpoints" aria-label="선택한 경로">
        <span>
          <small>출발</small>
          {pickupName(selectedHub.name)}
        </span>
        <b aria-hidden="true">→</b>
        <span>
          <small>도착</small>
          {destination.name}
        </span>
      </div>
    ) : null;
  return (
    <section className="trip-page">
      {activeRequest && (
        <aside
          className="mb-3 rounded-control bg-brand-soft px-4 py-3 text-sm"
          aria-label="기존 동승 상태"
        >
          <strong>
            {activeRequest.status === 'WAITING'
              ? '이미 동승자를 찾고 있어요'
              : '진행 중인 동승이 있어요'}
          </strong>
          <p className="mt-1">
            {activeRequest.status === 'WAITING'
              ? '다음 경로를 미리 고를 수 있어요. 바로 새로 요청하려면 대기 중인 요청을 먼저 취소해 주세요.'
              : activeRequest.status === 'MATCHED'
                ? '다음 경로를 미리 고를 수 있어요. 현재 매칭 상태는 동승 화면에서 확인해 주세요.'
                : '다음 경로를 미리 고를 수 있어요. 동승자 모두가 탑승 완료를 누르면 새 요청을 보낼 수 있어요.'}
          </p>
          <Link
            className="link px-0"
            to={activeRequest.groupId ? `/groups/${activeRequest.groupId}` : '/waiting'}
          >
            {activeRequest.groupId ? '현재 동승 보기' : '현재 요청 보기'} →
          </Link>
        </aside>
      )}
      <div className="trip-progress">
        {step !== 'origin' && (
          <button aria-label="이전 단계" onClick={back} disabled={createRequest.isPending}>
            ‹
          </button>
        )}
        <span>{stepLabels[step]}</span>
        <small>{index} / 3</small>
      </div>
      <div
        className="trip-track"
        role="progressbar"
        aria-label="동승 요청 단계"
        aria-valuemin={0}
        aria-valuemax={3}
        aria-valuenow={index}
        aria-valuetext={stepLabels[step]}
      >
        {[1, 2, 3].map((n) => (
          <span key={n} className={n <= index ? 'filled' : ''} />
        ))}
      </div>
      <div className="trip-step" key={step}>
        {step === 'origin' && (
          <>
            <h1>어디에서 출발할까요?</h1>
            <p className="trip-hint">함께 택시를 탈 장소를 골라 주세요.</p>
            <details className="hub-menu" open>
              <summary>출발 거점 선택</summary>
              <div>
                {sortedHubs.map((hub) => (
                  <button
                    type="button"
                    key={hub.id}
                    onClick={() => {
                      setHubId(hub.id);
                      if (
                        destination &&
                        destination.lat === hub.lat &&
                        destination.lng === hub.lng
                      ) {
                        setDestination(null);
                        setStep('destination');
                      } else setStep(editing ? 'review' : 'destination');
                      setEditing(false);
                    }}
                  >
                    {pickupName(hub.name)}
                    <span aria-hidden="true">›</span>
                  </button>
                ))}
              </div>
            </details>
            <div className="flex flex-wrap gap-2">
              <Button variant="ghost" loading={isLocating} onClick={requestCurrentLocation}>
                현재 위치에서 가까운 순
              </Button>
              {currentLocation && (
                <Button
                  variant="ghost"
                  onClick={() => {
                    setCurrentLocation(null);
                    setLocationMessage('기본 순서로 표시해요.');
                  }}
                >
                  기본 순서
                </Button>
              )}
            </div>
            {locationMessage && (
              <p role="status" className="text-sm text-slate-600">
                {locationMessage}
              </p>
            )}
          </>
        )}
        {step === 'destination' && selectedHub && (
          <>
            <h1 className="sr-only">어디까지 갈까요?</h1>
            <DestinationPicker
              origin={selectedHub}
              value={destination}
              recent={recentDestinations}
              onConfirm={handleDestinationChange}
            />
          </>
        )}
        {recentDestinationsError && (
          <p role="status" className="text-sm text-danger">
            {recentDestinationsError}
          </p>
        )}
        {step === 'conditions' && (
          <>
            <h1>어떤 조건으로 동승할까요?</h1>
            <p className="trip-hint">원하는 조건만 바꾸고 다음으로 넘어가세요.</p>
            {preview.isLoading && <p role="status">선택 가능한 인원을 확인하고 있어요.</p>}
            {preview.data === null && (
              <p className="trip-hint">현재 서버에서는 인원 지정 없이 매칭해요.</p>
            )}
            {preview.error && (
              <QueryState error={preview.error} retry={() => void preview.refetch()} />
            )}
            <MatchConditions
              sizes={sizeOptions ?? []}
              size={preferredSize}
              onSize={setPreferredSize}
              wait={maxWaitMin}
              sameGender={sameGenderOnly}
              detour={maxDetourRatio}
              onWait={setMaxWaitMin}
              onGender={setSameGenderOnly}
              onDetour={setMaxDetourRatio}
            />
            {route}
            <Button
              fullWidth
              disabled={preview.isPending || !!preview.error}
              onClick={() => {
                setEditing(false);
                setStep('review');
              }}
            >
              요청 확인
            </Button>
          </>
        )}
        {step === 'review' && selectedHub && destination && (
          <>
            <h1>이대로 동승을 요청할까요?</h1>
            <p className="trip-hint">경로와 조건을 눌러 다시 선택할 수 있어요.</p>
            <RouteMap
              sections={hasRoad ? preview.data!.sections : [{ path: [selectedHub, destination] }]}
              estimated={!hasRoad || preview.data?.estimated}
              stops={[
                {
                  order: 1,
                  kind: 'PICKUP',
                  position: selectedHub,
                  label: pickupName(selectedHub.name),
                },
                { order: 2, kind: 'DROPOFF', position: destination, label: destination.name },
              ]}
            />
            {(!hasRoad || preview.data?.estimated) && (
              <p className="trip-hint">
                점선은 두 지점을 이은 추정 경로예요. 실제 도로와 다를 수 있어요.
              </p>
            )}
            {preview.error && (
              <QueryState error={preview.error} retry={() => void preview.refetch()} />
            )}
            <div className="trip-review">
              {(
                [
                  ['출발 거점', pickupName(selectedHub.name), 'origin'],
                  ['목적지', destination.name, 'destination'],
                  [
                    '탑승 인원',
                    sizeOptions ? '나 포함 ' + preferredSize + '명' : '자동 매칭',
                    'conditions',
                  ],
                  ['대기 시간', maxWaitMin + '분', 'conditions'],
                  ['동승 조건', sameGenderOnly ? '동성만 매칭' : '성별 무관', 'conditions'],
                  ['허용 우회율', Math.round(maxDetourRatio * 100) + '%', 'conditions'],
                ] as const
              ).map(([label, value, target]) => (
                <button key={label} disabled={createRequest.isPending} onClick={() => edit(target)}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                  <i aria-hidden="true">›</i>
                </button>
              ))}
            </div>
            <Button
              fullWidth
              disabled={preview.isPending || !!preview.error || !!activeRequest}
              loading={createRequest.isPending}
              onClick={() => createRequest.mutate(selectedHub)}
            >
              {activeRequest ? '기존 요청 종료 후 접수 가능' : '동승 요청하기'}
            </Button>
          </>
        )}
      </div>
    </section>
  );
}
