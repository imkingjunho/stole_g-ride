package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.RideRequestExpired;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link RideRequestExpiryScheduler} 검증 (FR-10).
 *
 * <p>시각을 고정해 "지금"에 흔들리지 않게 한다.
 */
@ExtendWith(MockitoExtension.class)
class RideRequestExpirySchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final Clock FIXED = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Mock private RideRequestRepository rideRequestRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RideRequestExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RideRequestExpiryScheduler(rideRequestRepository, eventPublisher, FIXED);
    }

    private RideRequest request(Long id, Long userId) {
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
                        NOW.minusMinutes(20));
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    @DisplayName("만료된 요청을 EXPIRED 로 바꾸고 이벤트를 발행한다")
    void expiresOverdueRequests() {
        RideRequest overdue = request(1L, 10L);
        given(
                        rideRequestRepository.findByStatusAndExpiresAtLessThanEqual(
                                RideRequestStatus.WAITING, NOW))
                .willReturn(List.of(overdue));

        scheduler.expireOverdueRequests();

        assertThat(overdue.getStatus()).isEqualTo(RideRequestStatus.EXPIRED);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(RideRequestExpired.class);
        assertThat(((RideRequestExpired) event.getValue()).userId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("만료되면 진행 중 표시가 비워져 곧바로 다시 요청할 수 있다")
    void expiryClearsActiveMarker() {
        RideRequest overdue = request(1L, 10L);
        given(
                        rideRequestRepository.findByStatusAndExpiresAtLessThanEqual(
                                RideRequestStatus.WAITING, NOW))
                .willReturn(List.of(overdue));

        scheduler.expireOverdueRequests();

        assertThat(overdue.getActiveUserId()).isNull();
    }

    @Test
    @DisplayName("여러 건이면 각각 이벤트를 발행한다")
    void publishesPerRequest() {
        given(
                        rideRequestRepository.findByStatusAndExpiresAtLessThanEqual(
                                RideRequestStatus.WAITING, NOW))
                .willReturn(List.of(request(1L, 10L), request(2L, 11L), request(3L, 12L)));

        scheduler.expireOverdueRequests();

        verify(eventPublisher, times(3)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무 일도 하지 않는다")
    void doesNothingWhenNoneOverdue() {
        given(
                        rideRequestRepository.findByStatusAndExpiresAtLessThanEqual(
                                RideRequestStatus.WAITING, NOW))
                .willReturn(List.of());

        scheduler.expireOverdueRequests();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
