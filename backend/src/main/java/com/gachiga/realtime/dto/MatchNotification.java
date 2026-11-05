package com.gachiga.realtime.dto;

/**
 * 매칭 알림 push 본문. PRD §14.5 {@code /user/queue/match} 형식과 1:1 이다.
 *
 * @param type {@code PROPOSED}·{@code CONFIRMED}·{@code RECALCULATED}·{@code DISSOLVED}·
 *     {@code COMPLETED} 중 하나. 프론트와의 계약이므로 이름을 바꾸지 않는다
 */
public record MatchNotification(String type, Long groupId, String message) {

    public static MatchNotification proposed(Long groupId) {
        return new MatchNotification("PROPOSED", groupId, "매칭 상대를 찾았어요. 곧 확정됩니다.");
    }

    public static MatchNotification confirmed(Long groupId) {
        return new MatchNotification(
                "CONFIRMED", groupId, "매칭이 확정됐어요! 채팅방에서 만날 장소를 정해 보세요.");
    }

    public static MatchNotification recalculated(Long groupId) {
        return new MatchNotification(
                "RECALCULATED", groupId, "인원이 바뀌어 경로·요금이 다시 계산됐어요. 확인해 주세요.");
    }

    public static MatchNotification dissolved(Long groupId, String reason) {
        return new MatchNotification("DISSOLVED", groupId, dissolvedMessage(reason));
    }

    public static MatchNotification completed(Long groupId) {
        return new MatchNotification("COMPLETED", groupId, "탑승이 완료됐어요. 이용해 주셔서 감사합니다.");
    }

    /** {@code GroupDissolved.reason} 값을 보기 좋은 문구로 바꾼다. 모르는 값이면 일반 문구 */
    private static String dissolvedMessage(String reason) {
        if ("MEMBER_LEFT".equals(reason)) {
            return "동승자가 나가 그룹이 해체됐어요. 다시 대기열로 돌아갑니다.";
        }
        if ("TOO_FEW_MEMBERS".equals(reason)) {
            return "인원이 부족해 그룹이 해체됐어요. 다시 대기열로 돌아갑니다.";
        }
        return "그룹이 해체됐어요. 다시 대기열로 돌아갑니다.";
    }
}
