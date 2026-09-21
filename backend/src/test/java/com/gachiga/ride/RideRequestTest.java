package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RideRequest} 의 생성값과 상태 전이 규칙 검증.
 *
 * <p>DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). 시각을 인자로 받으므로 결과가 항상 같다.
 */
class RideRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 30);

    private RideRequest waitingRequest() {
        return RideRequest.create(
                1L,
                2L,
                "광주송정역",
                35.1378d,
                126.7902d,
                NOW.plusMinutes(5),
                10,
                true,
                new BigDecimal("0.20"),
                10_591,
                12_400,
                NOW);
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("대기 상태로 시작한다")
        void startsAsWaiting() {
            assertThat(waitingRequest().getStatus()).isEqualTo(RideRequestStatus.WAITING);
        }

        @Test
        @DisplayName("만료 시각은 생성 시각 + 최대 대기 시간이다")
        void expiresAfterMaxWait() {
            RideRequest request = waitingRequest();

            assertThat(request.getCreatedAt()).isEqualTo(NOW);
            assertThat(request.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        }

        @Test
        @DisplayName("단독 거리·요금을 그대로 보관한다 — 절감액의 기준선이라 나중에 바뀌면 안 된다")
        void keepsSoloFareAsBaseline() {
            RideRequest request = waitingRequest();

            assertThat(request.getSoloDistance()).isEqualTo(10_591);
            assertThat(request.getSoloFare()).isEqualTo(12_400);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transition {

        @Test
        @DisplayName("대기 → 배정 → 확정 → 완료 순서로 진행된다")
        void happyPath() {
            RideRequest request = waitingRequest();

            request.markMatched();
            assertThat(request.getStatus()).isEqualTo(RideRequestStatus.MATCHED);

            request.markConfirmed();
            assertThat(request.getStatus()).isEqualTo(RideRequestStatus.CONFIRMED);

            request.markCompleted();
            assertThat(request.getStatus()).isEqualTo(RideRequestStatus.COMPLETED);
        }

        @Test
        @DisplayName("대기 중이 아닌 요청은 배정할 수 없다 — 중복 배정 방지의 마지막 방어선 (E-02)")
        void cannotMatchTwice() {
            RideRequest request = waitingRequest();
            request.markMatched();

            assertThatThrownBy(request::markMatched)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("그룹이 해체되면 대기로 돌아가고 남은 시간은 유지된다 (E-01)")
        void dissolveKeepsExpiry() {
            RideRequest request = waitingRequest();
            LocalDateTime originalExpiry = request.getExpiresAt();
            request.markMatched();

            request.markWaiting();

            assertThat(request.getStatus()).isEqualTo(RideRequestStatus.WAITING);
            assertThat(request.getExpiresAt()).isEqualTo(originalExpiry);
        }

        @Test
        @DisplayName("진행 중인 요청은 취소할 수 있다")
        void cancelInProgress() {
            RideRequest request = waitingRequest();

            request.cancel();

            assertThat(request.getStatus()).isEqualTo(RideRequestStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 끝난 요청은 다시 취소할 수 없다")
        void cannotCancelFinished() {
            RideRequest request = waitingRequest();
            request.cancel();

            assertThatThrownBy(request::cancel)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("이미 종료된 요청");
        }
    }

    @Nested
    @DisplayName("만료 판정")
    class Expiry {

        @Test
        @DisplayName("만료 시각 전에는 만료가 아니다")
        void notExpiredBefore() {
            assertThat(waitingRequest().isExpiredAt(NOW.plusMinutes(9))).isFalse();
        }

        @Test
        @DisplayName("만료 시각 정각도 만료로 본다")
        void expiredExactlyAtDeadline() {
            assertThat(waitingRequest().isExpiredAt(NOW.plusMinutes(10))).isTrue();
        }

        @Test
        @DisplayName("남은 시간은 초 단위로 준다")
        void remainingSeconds() {
            assertThat(waitingRequest().remainingSeconds(NOW.plusMinutes(1))).isEqualTo(9 * 60);
        }

        @Test
        @DisplayName("이미 지났으면 남은 시간은 음수가 아니라 0이다")
        void remainingSecondsNeverNegative() {
            assertThat(waitingRequest().remainingSeconds(NOW.plusMinutes(30))).isZero();
        }
    }

    @Nested
    @DisplayName("소유 확인")
    class Ownership {

        @Test
        @DisplayName("본인 요청이면 true")
        void ownerMatches() {
            assertThat(waitingRequest().isOwnedBy(1L)).isTrue();
        }

        @Test
        @DisplayName("남의 요청이면 false — 취소 권한 검사에 쓴다")
        void otherUserDoesNotMatch() {
            assertThat(waitingRequest().isOwnedBy(2L)).isFalse();
        }
    }

    @Nested
    @DisplayName("진행 중 판정")
    class InProgress {

        @Test
        @DisplayName("WAITING·MATCHED·CONFIRMED 만 진행 중이다")
        void onlyThreeAreInProgress() {
            assertThat(RideRequestStatus.WAITING.isInProgress()).isTrue();
            assertThat(RideRequestStatus.MATCHED.isInProgress()).isTrue();
            assertThat(RideRequestStatus.CONFIRMED.isInProgress()).isTrue();
            assertThat(RideRequestStatus.CANCELLED.isInProgress()).isFalse();
            assertThat(RideRequestStatus.EXPIRED.isInProgress()).isFalse();
            assertThat(RideRequestStatus.COMPLETED.isInProgress()).isFalse();
        }
    }
}
