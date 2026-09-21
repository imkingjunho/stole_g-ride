package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RideGroupEventListener} 검증 — 그룹 쪽 사건이 요청 상태에 반영되는지.
 *
 * <p>수신 측이 예외를 밖으로 내보내지 않는 것도 함께 확인한다. 여기서 터지면 이미 커밋된
 * 그룹 처리가 되돌려지지 않은 채 로그만 남아야 한다 (CLAUDE.md §3).
 */
@ExtendWith(MockitoExtension.class)
class RideGroupEventListenerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);

    @Mock private RideRequestRepository rideRequestRepository;
    @InjectMocks private RideGroupEventListener listener;

    private RideRequest matchedRequest(Long userId) {
        RideRequest r =
                RideRequest.create(
                        userId,
                        2L,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        NOW.plusMinutes(5),
                        10,
                        false,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        NOW);
        r.markMatched();
        return r;
    }

    @Test
    @DisplayName("그룹 확정 → 요청도 확정된다")
    void confirmsMembers() {
        RideRequest a = matchedRequest(1L);
        RideRequest b = matchedRequest(2L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(2L), anyList()))
                .willReturn(Optional.of(b));

        listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.CONFIRMED);
        assertThat(b.getStatus()).isEqualTo(RideRequestStatus.CONFIRMED);
    }

    @Test
    @DisplayName("그룹 해체 → 대기로 돌아가고 남은 시간은 유지된다 (E-01)")
    void dissolveReturnsToQueueKeepingExpiry() {
        RideRequest a = matchedRequest(1L);
        LocalDateTime expiry = a.getExpiresAt();
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupDissolved(17L, List.of(1L), "MEMBER_LEFT"));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.WAITING);
        assertThat(a.getExpiresAt()).isEqualTo(expiry);
        assertThat(a.getActiveUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("탑승 완료 → 진행 중 표시가 비워져 다음 요청이 가능해진다")
    void completeClearsActiveMarker() {
        RideRequest a = matchedRequest(1L);
        a.markConfirmed();
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupCompleted(17L, List.of(1L)));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.COMPLETED);
        assertThat(a.getActiveUserId()).isNull();
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지는 처리한다")
    void oneFailureDoesNotStopOthers() {
        RideRequest b = matchedRequest(2L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willThrow(new IllegalStateException("DB 오류"));
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(2L), anyList()))
                .willReturn(Optional.of(b));

        listener.on(new GroupConfirmed(17L, List.of(1L, 2L)));

        assertThat(b.getStatus()).isEqualTo(RideRequestStatus.CONFIRMED);
    }

    @Test
    @DisplayName("요청을 못 찾아도 예외를 던지지 않는다 — 발행 측을 깨뜨리면 안 된다")
    void missingRequestIsLoggedNotThrown() {
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.empty());

        assertThatCode(() -> listener.on(new GroupConfirmed(17L, List.of(1L))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구성원이 비어 있어도 조용히 넘어간다")
    void emptyMembersIsIgnored() {
        assertThatCode(() -> listener.on(new GroupConfirmed(17L, List.of())))
                .doesNotThrowAnyException();
    }
}
