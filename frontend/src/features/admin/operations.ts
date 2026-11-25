import type { components } from '@/generated/api';
import { koreaTime } from '@/shared/api/format';
import { pickupName } from '@/shared/pickupName';

type Snapshot = components['schemas']['AdminDashboard'];
export const EXPIRING_MINUTES = 2;

export function readOperations({ waiting, matched, asOf }: Snapshot) {
  const now = koreaTime(asOf).getTime();
  const queue = waiting
    .map((row) => {
      const remaining = row.expiresAt
        ? Math.ceil((koreaTime(row.expiresAt).getTime() - now) / 60000)
        : null;
      return {
        ...row,
        hub: pickupName(row.hubName),
        elapsed: Math.max(0, Math.floor((now - koreaTime(row.requestedAt).getTime()) / 60000)),
        remaining,
        expiring: remaining !== null && remaining > 0 && remaining <= EXPIRING_MINUTES,
      };
    })
    .sort(
      (a, b) =>
        (a.remaining ?? Infinity) - (b.remaining ?? Infinity) ||
        a.requestedAt.localeCompare(b.requestedAt),
    );
  const members = matched.map((row) => ({ ...row, hub: pickupName(row.hubName) }));
  const hubs = new Map<
    string,
    { name: string; waiting: number; matched: number; longest: number }
  >();
  function hub(name: string) {
    const current = hubs.get(name);
    if (current) return current;
    const entry = { name, waiting: 0, matched: 0, longest: 0 };
    hubs.set(name, entry);
    return entry;
  }
  for (const row of queue) {
    const entry = hub(row.hub);
    entry.waiting++;
    entry.longest = Math.max(entry.longest, row.elapsed);
  }
  for (const row of members) hub(row.hub).matched++;
  return {
    queue,
    members,
    hubs: [...hubs.values()].sort(
      (a, b) =>
        b.waiting - a.waiting ||
        b.longest - a.longest ||
        b.matched - a.matched ||
        a.name.localeCompare(b.name, 'ko'),
    ),
    expiresKnown: queue.every((row) => row.remaining !== null),
    expiring: queue.filter((row) => row.expiring).length,
    pendingGroups: new Set(members.filter((row) => !row.confirmed).map((row) => row.groupId)).size,
    confirmedMembers: members.filter((row) => row.confirmed).length,
  };
}
export type Operations = ReturnType<typeof readOperations>;
