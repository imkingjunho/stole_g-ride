package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RideQueueInitializer} 검증.
 *
 * <p>Redis 가 비워진 채 서버가 뜨면 대기열이 사라진다. DB 에는 요청이 남아 있으므로
 * 기동 시 맞춰 주지 않으면 매칭이 아무도 찾지 못한다.
 */
@ExtendWith(MockitoExtension.class)
class RideQueueInitializerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);

    @Mock private RideRequestRepository rideRequestRepository;
    @Mock private RideQueue rideQueue;
    @Mock private HubPort hubPort;
    @InjectMocks private RideQueueInitializer initializer;

    private RideRequest request(Long id, Long hubId) {
        RideRequest r =
                RideRequest.create(
                        id,
                        hubId,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        NOW.plusMinutes(30),
                        20,
                        false,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private static HubInfo hub(Long id) {
        return new HubInfo(id, "거점" + id, 35.17, 126.91, "CAMPUS");
    }

    @Test
    @DisplayName("거점별로 묶어 각각 재구성한다")
    void rebuildsPerHub() {
        given(hubPort.findAll()).willReturn(List.of(hub(2L), hub(5L)));
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of(request(1L, 2L), request(2L, 2L), request(3L, 5L)));

        initializer.rebuildQueues();

        verify(rideQueue).rebuild(eq(2L), argThat(list -> list.size() == 2));
        verify(rideQueue).rebuild(eq(5L), argThat(list -> list.size() == 1));
    }

    @Test
    @DisplayName("대기자가 없는 거점은 빈 목록으로 다시 만들어 낡은 키를 지운다")
    void clearsHubsWithoutWaiting() {
        given(hubPort.findAll()).willReturn(List.of(hub(1L), hub(2L), hub(3L)));
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of(request(1L, 2L)));

        initializer.rebuildQueues();

        verify(rideQueue).rebuild(1L, List.of());
        verify(rideQueue).rebuild(eq(2L), argThat(list -> list.size() == 1));
        verify(rideQueue).rebuild(3L, List.of());
    }

    @Test
    @DisplayName("대기 중인 요청이 하나도 없어도 모든 거점을 비운다 — 끝난 요청이 Redis 에 남아 있을 수 있다")
    void clearsAllWhenNothingWaiting() {
        given(hubPort.findAll()).willReturn(List.of(hub(1L), hub(2L)));
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of());

        initializer.rebuildQueues();

        verify(rideQueue).rebuild(1L, List.of());
        verify(rideQueue).rebuild(2L, List.of());
    }

    @Test
    @DisplayName("거점 목록을 못 읽어도 대기자가 있는 거점은 재구성한다")
    void rebuildsWaitingHubsEvenIfHubListFails() {
        given(hubPort.findAll()).willThrow(new IllegalStateException("route 다운"));
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of(request(1L, 4L)));

        initializer.rebuildQueues();

        verify(rideQueue).rebuild(eq(4L), argThat(list -> list.size() == 1));
    }

    @Test
    @DisplayName("재구성에 실패해도 서버 기동을 막지 않는다 — 대기열은 색인일 뿐이다")
    void failureDoesNotBlockStartup() {
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> initializer.rebuildQueues()).doesNotThrowAnyException();
        verify(rideQueue, org.mockito.Mockito.never()).rebuild(anyLong(), any());
    }
}
