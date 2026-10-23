import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { components } from '@/generated/api';
import { api, isMockMode, showError, unwrap } from '@/shared/api/client';
import { Button, Card } from '@/shared/ui';
import { QueryState } from '@/shared/ui/QueryState';
import { DestinationPicker } from '@/features/map/DestinationPicker';

type Hub = components['schemas']['Hub'];
type Destination = { name: string; lat: number; lng: number };
type CreateRideRequest = components['schemas']['CreateRideRequest'];
const WAIT_OPTIONS: CreateRideRequest['maxWaitMin'][] = [5, 10, 15, 20];
const DETOUR_OPTIONS: CreateRideRequest['maxDetourRatio'][] = [0.1, 0.2, 0.3];

function localDateTime(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 19);
}

export function RequestPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [hubId, setHubId] = useState<number | null>(null);
  const [destination, setDestination] = useState<Destination | null>(null);
  const [maxWaitMin, setMaxWaitMin] = useState<CreateRideRequest['maxWaitMin']>(10);
  const [sameGenderOnly, setSameGenderOnly] = useState(true);
  const [maxDetourRatio, setMaxDetourRatio] =
    useState<CreateRideRequest['maxDetourRatio']>(0.2);

  const hubsQuery = useQuery({
    queryKey: ['hubs'],
    retry: false,
    queryFn: async () => {
      const response = await api.GET('/api/hubs');
      return unwrap(response.data, response.error) ?? [];
    },
  });
  const currentRequestQuery = useQuery({
    queryKey: ['myRequest'],
    enabled: !isMockMode,
    retry: false,
    queryFn: async () => {
      const response = await api.GET('/api/requests/me');
      return unwrap(response.data, response.error);
    },
  });

  useEffect(() => {
    if (hubId === null && hubsQuery.data?.length) setHubId(hubsQuery.data[0].id);
  }, [hubId, hubsQuery.data]);

  useEffect(() => {
    if (!isMockMode && currentRequestQuery.data) navigate('/waiting', { replace: true });
  }, [currentRequestQuery.data, navigate]);

  const createRequest = useMutation({
    mutationFn: async (hub: Hub) => {
      if (!destination) throw new Error('목적지를 선택해 주세요.');
      const body = {
        hubId: hub.id,
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
    onError: showError,
  });

  const selectedHub = hubsQuery.data?.find((hub) => hub.id === hubId);

  if (hubsQuery.isLoading || (!isMockMode && currentRequestQuery.isLoading)) {
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

  return (
    <section className="page">
      <header>
        <p className="text-sm font-semibold text-brand">가치가 · 함께 타고, 공정하게 나눠요</p>
        <h1 className="mt-2 text-title font-bold">어디로 갈까요?</h1>
        <p className="mt-2 text-sm text-slate-600">출발 거점과 목적지를 선택해 동승을 요청하세요.</p>
      </header>

      <Card className="space-y-4">
        <fieldset>
          <legend className="mb-3 text-sm font-semibold">출발 거점</legend>
          <div className="grid grid-cols-2 gap-2">
            {hubsQuery.data.map((hub) => (
              <button
                type="button"
                key={hub.id}
                aria-pressed={hub.id === hubId}
                onClick={() => setHubId(hub.id)}
                className={`min-h-touch rounded-control border px-3 py-3 text-sm font-semibold ${hub.id === hubId ? 'border-brand bg-brand-soft text-brand' : 'border-line bg-white'}`}
              >
                {hub.name}
              </button>
            ))}
          </div>
        </fieldset>

        <DestinationPicker value={destination} onChange={setDestination} />

        <fieldset>
          <legend className="mb-3 text-sm font-semibold">최대 대기시간</legend>
          <div className="grid grid-cols-4 gap-2">
            {WAIT_OPTIONS.map((minutes) => (
              <button
                type="button"
                key={minutes}
                aria-pressed={minutes === maxWaitMin}
                onClick={() => setMaxWaitMin(minutes)}
                className={`min-h-touch rounded-control border text-sm font-semibold ${minutes === maxWaitMin ? 'border-brand bg-brand-soft text-brand' : 'border-line bg-white'}`}
              >
                {minutes}분
              </button>
            ))}
          </div>
        </fieldset>

        <label className="flex min-h-touch items-center justify-between gap-4">
          <span>
            <span className="block text-sm font-semibold">동성끼리 매칭</span>
            <span className="mt-1 block text-xs text-slate-600">원할 때만 켜 두세요.</span>
          </span>
          <input
            type="checkbox"
            className="h-6 w-6 accent-brand"
            checked={sameGenderOnly}
            onChange={(event) => setSameGenderOnly(event.target.checked)}
          />
        </label>

        <fieldset>
          <legend className="mb-3 text-sm font-semibold">최대 허용 우회율</legend>
          <div className="grid grid-cols-3 gap-2">
            {DETOUR_OPTIONS.map((ratio) => (
              <button
                type="button"
                key={ratio}
                aria-pressed={ratio === maxDetourRatio}
                onClick={() => setMaxDetourRatio(ratio)}
                className={`min-h-touch rounded-control border text-sm font-semibold ${ratio === maxDetourRatio ? 'border-brand bg-brand-soft text-brand' : 'border-line bg-white'}`}
              >
                {Math.round(ratio * 100)}%
              </button>
            ))}
          </div>
        </fieldset>
      </Card>

      <Button
        fullWidth
        loading={createRequest.isPending}
        disabled={!selectedHub || !destination}
        onClick={() => selectedHub && createRequest.mutate(selectedHub)}
      >
        동승 매칭 시작
      </Button>
    </section>
  );
}
