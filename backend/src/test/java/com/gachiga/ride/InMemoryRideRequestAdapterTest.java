package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.route.Coordinate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryRideRequestAdapter} 검증.
 *
 * <p>Phase 1 에 JPA + Redis 구현으로 바뀌어도 <b>지켜야 할 규칙은 같다</b>: 만료 30초 전 제외(E-04),
 * 낙관적 락으로 중복 배정 차단(E-02). 그래서 이 테스트는 교체 후에도 그대로 쓸 수 있다.
 */
class InMemoryRideRequestAdapterTest {

    private InMemoryRideRequestAdapter adapter;

    private static final Coordinate DESTINATION = new Coordinate(35.1372, 126.7913);

    @BeforeEach
    void setUp() {
        adapter = new InMemoryRideRequestAdapter();
    }

    @Test
    @DisplayName("대기 중인 요청을 돌려준다")
    void findsWaitingRequests() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));

        assertThat(adapter.findWaiting()).hasSize(1);
    }

    @Test
    @DisplayName("만료까지 30초도 안 남은 요청은 제외한다 (E-04)")
    void excludesRequestsAboutToExpire() {
        adapter.put(request(1L, 1L, 2L, LocalDateTime.now().plusSeconds(20)));
        adapter.put(request(2L, 2L, 2L, LocalDateTime.now().plusSeconds(90)));

        assertThat(adapter.findWaiting())
                .extracting(WaitingRequest::requestId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("희망 출발 시각이 이른 순서로 돌려준다")
    void sortsByDepartAt() {
        adapter.put(requestWithDepartAt(1L, minutesFromNow(30)));
        adapter.put(requestWithDepartAt(2L, minutesFromNow(10)));
        adapter.put(requestWithDepartAt(3L, minutesFromNow(20)));

        assertThat(adapter.findWaiting())
                .extracting(WaitingRequest::requestId)
                .containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("배정에 성공하면 true 이고 더 이상 대기 목록에 없다")
    void markMatchedRemovesFromWaiting() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));

        assertThat(adapter.tryMarkMatched(1L, 0)).isTrue();
        assertThat(adapter.findWaiting()).isEmpty();
    }

    @Test
    @DisplayName("같은 요청을 두 번째로 배정하려 하면 false 다 (E-02 중복 배정 차단)")
    void secondMatchAttemptFails() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));

        assertThat(adapter.tryMarkMatched(1L, 0)).isTrue();
        assertThat(adapter.tryMarkMatched(1L, 0)).isFalse();
    }

    @Test
    @DisplayName("버전이 어긋나면 배정에 실패한다")
    void staleVersionFails() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));

        assertThat(adapter.tryMarkMatched(1L, 7)).isFalse();
    }

    @Test
    @DisplayName("없는 요청을 배정하려 하면 false 다")
    void unknownRequestFails() {
        assertThat(adapter.tryMarkMatched(999L, 0)).isFalse();
    }

    @Test
    @DisplayName("그룹이 해체되면 다시 대기열로 돌아간다 (E-01)")
    void markWaitingReturnsToQueue() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));
        adapter.tryMarkMatched(1L, 0);

        adapter.markWaiting(1L);

        assertThat(adapter.findWaiting()).extracting(WaitingRequest::requestId).containsExactly(1L);
    }

    @Test
    @DisplayName("대기 상태에 남은 시간과 같은 거점 후보 수가 담긴다")
    void queueStatusHasRemainingSecondsAndCandidates() {
        adapter.put(request(1L, 1L, 2L, LocalDateTime.now().plusMinutes(5)));
        adapter.put(request(2L, 2L, 2L, LocalDateTime.now().plusMinutes(5))); // 같은 거점
        adapter.put(request(3L, 3L, 9L, LocalDateTime.now().plusMinutes(5))); // 다른 거점

        QueueStatus status = adapter.statusOf(1L).orElseThrow();

        assertThat(status.requestId()).isEqualTo(1L);
        assertThat(status.status()).isEqualTo("WAITING");
        assertThat(status.remainingSeconds()).isBetween(280, 300);
        assertThat(status.candidateCount()).isEqualTo(1); // 나 자신 제외, 같은 거점만
    }

    @Test
    @DisplayName("진행 중인 요청이 없으면 빈 값이다")
    void noRequestMeansEmpty() {
        assertThat(adapter.statusOf(42L)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("탑승이 끝난 요청은 진행 중으로 보지 않는다")
    void completedRequestIsNotInProgress() {
        adapter.put(request(1L, 1L, 2L, minutesFromNow(10)));
        adapter.markCompleted(1L);

        assertThat(adapter.statusOf(1L)).isEmpty();
    }

    @Test
    @DisplayName("이미 만료된 요청의 남은 시간은 음수가 아니라 0이다")
    void expiredRequestHasZeroRemainingSeconds() {
        adapter.put(request(1L, 1L, 2L, LocalDateTime.now().minusMinutes(1)));

        assertThat(adapter.statusOf(1L).orElseThrow().remainingSeconds()).isZero();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private LocalDateTime minutesFromNow(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }

    private WaitingRequest request(Long requestId, Long userId, Long hubId, LocalDateTime expiresAt) {
        return new WaitingRequest(
                requestId,
                userId,
                hubId,
                DESTINATION,
                "광주송정역",
                LocalDateTime.now().plusMinutes(5),
                expiresAt,
                LocalDateTime.now(),
                false,
                new BigDecimal("0.30"),
                10_000,
                12_400,
                0);
    }

    private WaitingRequest requestWithDepartAt(Long requestId, LocalDateTime departAt) {
        return new WaitingRequest(
                requestId,
                requestId,
                2L,
                DESTINATION,
                "광주송정역",
                departAt,
                minutesFromNow(30),
                LocalDateTime.now(),
                false,
                new BigDecimal("0.30"),
                10_000,
                12_400,
                0);
    }
}
