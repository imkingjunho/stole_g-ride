package com.gachiga.realtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MatchNotification} 정적 팩터리 검증. {@code type} 문자열은 PRD §14.5·프론트와의 계약이라
 * 정확한 값이 나가는지가 가장 중요하다.
 */
class MatchNotificationTest {

    @Test
    @DisplayName("타입 문자열이 §14.5 그대로다")
    void typesMatchProtocol() {
        assertThat(MatchNotification.proposed(1L).type()).isEqualTo("PROPOSED");
        assertThat(MatchNotification.confirmed(1L).type()).isEqualTo("CONFIRMED");
        assertThat(MatchNotification.recalculated(1L).type()).isEqualTo("RECALCULATED");
        assertThat(MatchNotification.dissolved(1L, "MEMBER_LEFT").type()).isEqualTo("DISSOLVED");
        assertThat(MatchNotification.completed(1L).type()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("groupId를 그대로 담는다")
    void keepsGroupId() {
        assertThat(MatchNotification.confirmed(17L).groupId()).isEqualTo(17L);
    }

    @Test
    @DisplayName("해체 사유별로 다른 문구를 만든다")
    void dissolvedMessageVariesByReason() {
        String memberLeft = MatchNotification.dissolved(1L, "MEMBER_LEFT").message();
        String tooFew = MatchNotification.dissolved(1L, "TOO_FEW_MEMBERS").message();
        String unknown = MatchNotification.dissolved(1L, "SOMETHING_ELSE").message();

        assertThat(memberLeft).contains("동승자");
        assertThat(tooFew).contains("인원");
        assertThat(unknown).doesNotContain("null");
    }
}
