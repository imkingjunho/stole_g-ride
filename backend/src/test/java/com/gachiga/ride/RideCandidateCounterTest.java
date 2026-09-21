package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RideCandidateCounter} 검증.
 *
 * <p>REST 응답과 WebSocket push 가 같은 숫자를 보여 줘야 하므로 계산이 한 곳에 있다.
 * 여기가 틀리면 대기 화면의 "함께 기다리는 사람" 이 깜빡이며 달라진다.
 */
@ExtendWith(MockitoExtension.class)
class RideCandidateCounterTest {

    private static final Long HUB_ID = 2L;

    @Mock private RideRequestRepository rideRequestRepository;
    @InjectMocks private RideCandidateCounter counter;

    private RideRequest requestWith(RideRequestStatus status) {
        RideRequest request =
                RideRequest.create(
                        1L,
                        HUB_ID,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        LocalDateTime.now().plusMinutes(5),
                        10,
                        false,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        LocalDateTime.now());
        if (status == RideRequestStatus.MATCHED) {
            request.markMatched();
        } else if (status == RideRequestStatus.CONFIRMED) {
            request.markMatched();
            request.markConfirmed();
        }
        return request;
    }

    @Test
    @DisplayName("내가 대기 중이면 집계에 내가 포함되므로 1을 뺀다")
    void subtractsSelfWhenWaiting() {
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(4L);

        assertThat(counter.countFor(requestWith(RideRequestStatus.WAITING))).isEqualTo(3);
    }

    @Test
    @DisplayName("내가 매칭 상태면 집계에 내가 없으므로 빼지 않는다")
    void doesNotSubtractWhenMatched() {
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(4L);

        assertThat(counter.countFor(requestWith(RideRequestStatus.MATCHED))).isEqualTo(4);
    }

    @Test
    @DisplayName("내가 확정 상태여도 빼지 않는다")
    void doesNotSubtractWhenConfirmed() {
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(2L);

        assertThat(counter.countFor(requestWith(RideRequestStatus.CONFIRMED))).isEqualTo(2);
    }

    @Test
    @DisplayName("나 혼자 대기 중이면 0이다 — 음수가 되지 않는다")
    void neverNegative() {
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(1L);

        assertThat(counter.countFor(requestWith(RideRequestStatus.WAITING))).isZero();
    }
}
