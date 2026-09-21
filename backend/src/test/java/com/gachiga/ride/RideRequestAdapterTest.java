package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.WaitingRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RideRequestAdapter} 검증 — 다른 모듈이 보는 창구다.
 *
 * <p>가장 중요한 것은 <b>중복 배정 차단</b>(E-02)과 <b>만료 30초 전 제외</b>(E-04)다.
 * 둘 다 틀리면 성사된 그룹이 곧바로 깨진다.
 */
@ExtendWith(MockitoExtension.class)
class RideRequestAdapterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final Clock FIXED = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Mock private RideRequestRepository rideRequestRepository;
    @Mock private RideCandidateCounter candidateCounter;

    private RideRequestAdapter adapter;

    @BeforeEach
    void setUp() {
        RideProperties properties =
                new RideProperties(
                        List.of(5, 10, 15, 20),
                        10,
                        List.of(new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.30")),
                        500,
                        30,
                        30);
        adapter =
                new RideRequestAdapter(
                        rideRequestRepository, properties, candidateCounter, FIXED);
    }

    private RideRequest request(Long id) {
        RideRequest r =
                RideRequest.create(
                        1L,
                        2L,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        NOW.plusMinutes(30),
                        10,
                        true,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    @DisplayName("만료 30초 전까지만 매칭 대상에 넣는다 (E-04)")
    void findWaitingUsesCutoff() {
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of(request(1L)));

        adapter.findWaiting();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(rideRequestRepository)
                .findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                        eq(RideRequestStatus.WAITING), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    @DisplayName("엔티티가 아니라 계약 레코드로 옮겨 준다 — 남이 내부 구조를 보지 않는다")
    void mapsToContractRecord() {
        given(
                        rideRequestRepository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                                eq(RideRequestStatus.WAITING), any(LocalDateTime.class)))
                .willReturn(List.of(request(7L)));

        List<WaitingRequest> waiting = adapter.findWaiting();

        assertThat(waiting).hasSize(1);
        WaitingRequest first = waiting.get(0);
        assertThat(first.requestId()).isEqualTo(7L);
        assertThat(first.hubId()).isEqualTo(2L);
        assertThat(first.destination().lat()).isEqualTo(35.1378d);
        assertThat(first.destName()).isEqualTo("광주송정역");
        assertThat(first.soloFare()).isEqualTo(15_300);
        assertThat(first.sameGenderOnly()).isTrue();
    }

    @Test
    @DisplayName("배정에 성공하면 true — 조건부 UPDATE 가 1건을 바꿨을 때")
    void matchSucceedsWhenRowUpdated() {
        given(rideRequestRepository.markMatchedIfVersionMatches(7L, 0)).willReturn(1);

        assertThat(adapter.tryMarkMatched(7L, 0)).isTrue();
    }

    @Test
    @DisplayName("남이 먼저 가져갔으면 false — 0건 갱신 (E-02 중복 배정 차단)")
    void matchFailsWhenAlreadyTaken() {
        given(rideRequestRepository.markMatchedIfVersionMatches(7L, 0)).willReturn(0);

        assertThat(adapter.tryMarkMatched(7L, 0)).isFalse();
    }

    @Test
    @DisplayName("해체되면 대기로 돌아가고 남은 시간은 유지된다 (E-01)")
    void markWaitingKeepsExpiry() {
        RideRequest matched = request(7L);
        matched.markMatched();
        LocalDateTime expiry = matched.getExpiresAt();
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(matched));

        adapter.markWaiting(7L);

        assertThat(matched.getStatus()).isEqualTo(RideRequestStatus.WAITING);
        assertThat(matched.getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    @DisplayName("완료 처리하면 진행 중 표시가 비워져 새 요청을 만들 수 있다")
    void completeClearsActiveMarker() {
        RideRequest confirmed = request(7L);
        confirmed.markMatched();
        confirmed.markConfirmed();
        given(rideRequestRepository.findById(7L)).willReturn(Optional.of(confirmed));

        adapter.markCompleted(7L);

        assertThat(confirmed.getStatus()).isEqualTo(RideRequestStatus.COMPLETED);
        assertThat(confirmed.getActiveUserId()).isNull();
    }

    @Test
    @DisplayName("없는 요청에 상태 전이를 걸어도 예외를 던지지 않는다 — 매칭 쪽 흐름을 깨면 안 된다")
    void missingRequestIsIgnored() {
        given(rideRequestRepository.findById(99L)).willReturn(Optional.empty());

        adapter.markConfirmed(99L);
        adapter.markCompleted(99L);
        adapter.markWaiting(99L);
    }

    @Test
    @DisplayName("대기 상태를 남은 시간·후보 수와 함께 돌려준다 (FR-09)")
    void reportsQueueStatus() {
        given(
                        rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                                eq(1L), anyList()))
                .willReturn(Optional.of(request(7L)));
        given(candidateCounter.countFor(any(RideRequest.class))).willReturn(2);

        QueueStatus status = adapter.statusOf(1L).orElseThrow();

        assertThat(status.requestId()).isEqualTo(7L);
        assertThat(status.status()).isEqualTo("WAITING");
        assertThat(status.remainingSeconds()).isEqualTo(600);
        assertThat(status.candidateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("진행 중인 요청이 없으면 빈 값이다")
    void noRequestGivesEmptyStatus() {
        given(
                        rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                                eq(1L), anyList()))
                .willReturn(Optional.empty());

        assertThat(adapter.statusOf(1L)).isEmpty();
    }
}
