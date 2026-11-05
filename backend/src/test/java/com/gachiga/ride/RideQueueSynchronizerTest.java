package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.event.RideRequestExpired;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RideQueueSynchronizer} 검증 — DB 와 Redis 대기열을 맞추는 지점이다.
 *
 * <p>여기가 틀리면 둘이 어긋나 매칭이 없는 요청을 집거나 있는 요청을 놓친다.
 * 가장 중요한 성질은 <b>어떤 실패도 밖으로 새지 않는다</b>는 것이다 — 이 코드는 이미 커밋된
 * 트랜잭션 뒤에 돌기 때문에 여기서 던져 봐야 되돌릴 것이 없다.
 */
@ExtendWith(MockitoExtension.class)
class RideQueueSynchronizerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final LocalDateTime DEPART_AT = NOW.plusMinutes(30);

    @Mock private RideQueue rideQueue;
    @Mock private RideRequestRepository rideRequestRepository;
    @InjectMocks private RideQueueSynchronizer synchronizer;

    private RideRequest request(Long id, Long hubId) {
        RideRequest r =
                RideRequest.create(
                        1L,
                        hubId,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        DEPART_AT,
                        10,
                        false,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    @DisplayName("요청이 생기면 그 거점 대기열에 출발 시각을 점수로 넣는다")
    void addsOnCreated() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 2L)));

        synchronizer.on(new RideRequestCreated(7L, 1L, 2L));

        verify(rideQueue).add(2L, 7L, DEPART_AT);
    }

    @Test
    @DisplayName("취소되면 대기열에서 뺀다")
    void removesOnCancelled() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 2L)));

        synchronizer.on(new RideRequestCancelled(7L, 1L));

        verify(rideQueue).remove(2L, 7L);
    }

    @Test
    @DisplayName("만료되면 대기열에서 뺀다 (FR-10)")
    void removesOnExpired() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 5L)));

        synchronizer.on(new RideRequestExpired(7L, 1L));

        verify(rideQueue).remove(5L, 7L);
    }

    @Test
    @DisplayName("거점 id 를 알아야 키를 만들 수 있어 요청을 다시 읽는다 — 이벤트에는 없다")
    void readsRequestToLearnHubId() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 3L)));

        synchronizer.on(new RideRequestCancelled(7L, 1L));

        verify(rideRequestRepository).findById(7L);
        verify(rideQueue).remove(3L, 7L);
    }

    @Test
    @DisplayName("요청이 사라졌으면 대기열을 건드리지 않는다")
    void missingRequestTouchesNothing() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.empty());

        synchronizer.on(new RideRequestCreated(7L, 1L, 2L));

        verifyNoInteractions(rideQueue);
    }

    @Test
    @DisplayName("대기열이 터져도 예외를 밖으로 내보내지 않는다 — 요청은 이미 커밋됐다")
    void queueFailureIsSwallowed() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 2L)));
        willThrow(new IllegalStateException("redis down"))
                .given(rideQueue)
                .add(anyLong(), anyLong(), any(LocalDateTime.class));

        assertThatCode(() -> synchronizer.on(new RideRequestCreated(7L, 1L, 2L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DB 조회가 터져도 예외를 밖으로 내보내지 않는다")
    void repositoryFailureIsSwallowed() {
        given(rideRequestRepository.findById(7L)).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> synchronizer.on(new RideRequestExpired(7L, 1L)))
                .doesNotThrowAnyException();
        verify(rideQueue, never()).remove(anyLong(), anyLong());
    }

    @Test
    @DisplayName("상태 변경 신호: 지금 WAITING 이면 대기열에 넣는다 — 그룹 해체 뒤 재대기 (E-01)")
    void statusChangedToWaitingAdds() {
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(request(7L, 2L)));

        synchronizer.on(new RideRequestStatusChanged(7L));

        verify(rideQueue).add(2L, 7L, DEPART_AT);
        verify(rideQueue, org.mockito.Mockito.never()).remove(any(), any());
    }

    @Test
    @DisplayName("상태 변경 신호: WAITING 이 아니면 대기열에서 뺀다 — 매칭 배정·확정·완료")
    void statusChangedAwayFromWaitingRemoves() {
        RideRequest matched = request(7L, 2L);
        matched.markMatched();
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(matched));

        synchronizer.on(new RideRequestStatusChanged(7L));

        verify(rideQueue).remove(2L, 7L);
        verify(rideQueue, org.mockito.Mockito.never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("상태 변경 신호 처리가 실패해도 예외를 밖으로 내지 않는다")
    void statusChangedFailureIsSwallowed() {
        given(rideRequestRepository.findById(7L)).willThrow(new IllegalStateException("db down"));

        org.assertj.core.api.Assertions.assertThatCode(
                        () -> synchronizer.on(new RideRequestStatusChanged(7L)))
                .doesNotThrowAnyException();
    }
}
