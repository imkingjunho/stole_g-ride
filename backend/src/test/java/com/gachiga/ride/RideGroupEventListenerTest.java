package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.GroupCompleted;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.event.GroupProposed;
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
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock private ApplicationEventPublisher eventPublisher;
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
        // GroupProposed 를 놓쳤어도 확정된 그룹은 GET /api/requests/me 로 찾을 수 있어야 한다
        assertThat(a.getGroupId()).isEqualTo(17L);
        assertThat(b.getGroupId()).isEqualTo(17L);
        verify(eventPublisher, times(2)).publishEvent(any(RideRequestStatusChanged.class));
    }

    @Test
    @DisplayName("그룹 생성 → 그룹 id 만 적고 상태는 그대로 둔다 (MATCHED 전이는 tryMarkMatched 몫)")
    void proposedAssignsGroupIdOnly() {
        RideRequest a = matchedRequest(1L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupProposed(21L, List.of(1L)));

        assertThat(a.getGroupId()).isEqualTo(21L);
        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.MATCHED);
    }

    @Test
    @DisplayName("그룹 해체 → 그룹 id 를 비운다. 남겨 두면 대기 화면이 없어진 그룹으로 넘어간다")
    void dissolveClearsGroupId() {
        RideRequest a = matchedRequest(1L);
        a.assignGroup(21L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupDissolved(21L, List.of(1L), "MEMBER_LEFT"));

        assertThat(a.getGroupId()).isNull();
        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.WAITING);
    }

    @Test
    @DisplayName("그룹 해체 → 대기로 돌아가고 남은 시간은 유지된다 (E-01)")
    void dissolveReturnsToQueueKeepingExpiry() {
        RideRequest a = matchedRequest(1L);
        a.assignGroup(17L);
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

    // ── 늦게 오거나 순서가 뒤바뀐 이벤트 (T2-3 리뷰 TX-1) ─────────────

    @Test
    @DisplayName("해체 뒤에 늦게 온 확정은 적용하지 않는다 — 해체된 그룹이 되살아나 요청이 CONFIRMED 로 굳는다")
    void lateConfirmAfterDissolveIsIgnored() {
        RideRequest a = matchedRequest(1L);
        a.assignGroup(10L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));
        listener.on(new GroupDissolved(10L, List.of(1L), "MEMBER_LEFT"));

        listener.on(new GroupConfirmed(10L, List.of(1L)));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.WAITING);
        assertThat(a.getGroupId()).isNull();
    }

    @Test
    @DisplayName("그룹을 모르는 MATCHED 요청에 해체가 오면 되돌리지 않는다 — 방금 다른 그룹에 배정된 것일 수 있다")
    void dissolveOfUnknownGroupDoesNotRequeue() {
        // 해체(10) 뒤 같은 tick 에서 새 그룹(11)에 배정됐고, GroupProposed(11) 은 아직 처리 전이다
        RideRequest a = matchedRequest(1L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupDissolved(10L, List.of(1L), "MEMBER_LEFT"));
        listener.on(new GroupProposed(11L, List.of(1L)));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.MATCHED);
        assertThat(a.getGroupId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("다른 그룹의 해체는 적용하지 않는다 — 두 번 배정되는 것을 막는다")
    void dissolveOfOtherGroupIsIgnored() {
        RideRequest a = matchedRequest(1L);
        a.assignGroup(11L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupDissolved(10L, List.of(1L), "MEMBER_LEFT"));

        assertThat(a.getStatus()).isEqualTo(RideRequestStatus.MATCHED);
        assertThat(a.getGroupId()).isEqualTo(11L);
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(RideRequestStatusChanged.class));
    }

    @Test
    @DisplayName("이미 다른 그룹이 적힌 요청에 생성 이벤트가 와도 덮어쓰지 않는다")
    void proposedForOtherGroupDoesNotOverwrite() {
        RideRequest a = matchedRequest(1L);
        a.assignGroup(10L);
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyList()))
                .willReturn(Optional.of(a));

        listener.on(new GroupProposed(11L, List.of(1L)));

        assertThat(a.getGroupId()).isEqualTo(10L);
    }
}
