package com.gachiga.contract.event;

import java.util.List;

/**
 * 그룹이 해체됐다. 구성원의 요청은 다시 대기열로 돌아간다.
 *
 * <p>발행 {@code matching/}(서준) → 수신 {@code realtime/}(임승현, 시스템 메시지 후 방 삭제 예약 — E-10),
 * {@code ride/}(이승민, 요청을 WAITING 으로 되돌림).
 *
 * @param groupId 그룹 id
 * @param userIds 해체 시점의 구성원 사용자 id 목록. null 아님
 * @param reason  해체 사유. 예: {@code "MEMBER_LEFT"}, {@code "TOO_FEW_MEMBERS"}. 비교는 {@code equals}
 */
public record GroupDissolved(Long groupId, List<Long> userIds, String reason) {}
