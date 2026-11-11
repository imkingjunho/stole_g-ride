import type { components } from '@/generated/api';
import { koreaTime } from '@/shared/api/format';

type Group = components['schemas']['GroupDetail'];
const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const text = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0;
const nonnegative = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;
const money = (value: unknown): value is number =>
  nonnegative(value) && Number.isSafeInteger(value);
const id = (value: unknown): value is number => money(value) && value > 0;
const coordinate = (value: unknown): boolean =>
  record(value) &&
  typeof value.lat === 'number' &&
  Number.isFinite(value.lat) &&
  typeof value.lng === 'number' &&
  Number.isFinite(value.lng);

export class GroupResponseError extends Error {
  constructor() {
    super('동승 정보를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    this.name = 'GroupResponseError';
  }
}

/** OpenAPI 타입의 런타임 경계. 누락된 서버 값을 임의의 요금·좌표로 채우지 않는다. */
export function readGroupDetail(
  value: unknown,
  groupId: number,
  userId: number | undefined,
): Group {
  if (
    !record(value) ||
    value.groupId !== groupId ||
    !id(value.groupId) ||
    typeof value.status !== 'string' ||
    !['PENDING', 'CONFIRMED', 'COMPLETED', 'DISSOLVED'].includes(value.status) ||
    typeof value.estimated !== 'boolean' ||
    !record(value.hub) ||
    !id(value.hub.id) ||
    !text(value.hub.name) ||
    !coordinate(value.hub) ||
    !money(value.totalFare) ||
    !nonnegative(value.totalDistance) ||
    !nonnegative(value.totalDuration) ||
    !record(value.route) ||
    !Array.isArray(value.route.sections) ||
    !Array.isArray(value.members)
  ) {
    throw new GroupResponseError();
  }
  for (const section of value.route.sections) {
    if (
      !record(section) ||
      !nonnegative(section.distance) ||
      !nonnegative(section.duration) ||
      !Array.isArray(section.path) ||
      !section.path.every(coordinate)
    )
      throw new GroupResponseError();
  }
  const users = new Set<number>();
  for (const member of value.members) {
    if (
      !record(member) ||
      !id(member.userId) ||
      users.has(member.userId) ||
      !text(member.nickname) ||
      typeof member.isMe !== 'boolean' ||
      member.isMe !== (member.userId === userId) ||
      !id(member.boardingOrder) ||
      !id(member.dropoffOrder) ||
      !text(member.destName) ||
      !money(member.shareAmount) ||
      !money(member.soloFare) ||
      !money(member.savingAmount) ||
      !nonnegative(member.detourRatio) ||
      typeof member.accepted !== 'boolean'
    )
      throw new GroupResponseError();
    users.add(member.userId);
    if (member.breakdown !== undefined) {
      if (!Array.isArray(member.breakdown)) throw new GroupResponseError();
      for (const item of member.breakdown) {
        if (
          !record(item) ||
          !money(item.sectionIndex) ||
          item.sectionIndex >= value.route.sections.length ||
          !nonnegative(item.sectionFare) ||
          !id(item.onboardCount) ||
          !nonnegative(item.share)
        )
          throw new GroupResponseError();
      }
    }
    if (
      member.roundingAdjustment !== undefined &&
      (typeof member.roundingAdjustment !== 'number' || !Number.isFinite(member.roundingAdjustment))
    )
      throw new GroupResponseError();
  }
  if (
    !userId ||
    !users.has(userId) ||
    (value.acceptDeadline != null &&
      (typeof value.acceptDeadline !== 'string' ||
        !Number.isFinite(koreaTime(value.acceptDeadline).getTime())))
  )
    throw new GroupResponseError();
  return value as Group;
}
