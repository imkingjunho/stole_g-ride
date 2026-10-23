import type { components } from '@/generated/api';

type Hub = components['schemas']['Hub'];
export interface Destination {
  name: string;
  lat: number;
  lng: number;
}

export const RECENT_DESTINATIONS_KEY = 'gachiga:recent-destinations:v1';
export const MAX_RECENT_DESTINATIONS = 3;

export function isDestination(value: unknown): value is Destination {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.name === 'string' &&
    candidate.name.length > 0 &&
    typeof candidate.lat === 'number' &&
    Number.isFinite(candidate.lat) &&
    candidate.lat >= -90 &&
    candidate.lat <= 90 &&
    typeof candidate.lng === 'number' &&
    Number.isFinite(candidate.lng) &&
    candidate.lng >= -180 &&
    candidate.lng <= 180
  );
}

export function isDestinationList(value: unknown): value is Destination[] {
  return Array.isArray(value) && value.every(isDestination);
}

export function sortHubsByDistance(hubs: Hub[], origin: Destination): Hub[] {
  return hubs
    .map((hub, index) => ({ hub, index, distance: distanceBetween(hub, origin) }))
    .sort((left, right) => left.distance - right.distance || left.index - right.index)
    .map(({ hub }) => hub);
}

function distanceBetween(
  first: Pick<Destination, 'lat' | 'lng'>,
  second: Pick<Destination, 'lat' | 'lng'>,
): number {
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitudeDelta = radians(second.lat - first.lat);
  const longitudeDelta = radians(second.lng - first.lng);
  const latitude1 = radians(first.lat);
  const latitude2 = radians(second.lat);
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(latitude1) * Math.cos(latitude2) * Math.sin(longitudeDelta / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(Math.min(1, haversine)));
}
