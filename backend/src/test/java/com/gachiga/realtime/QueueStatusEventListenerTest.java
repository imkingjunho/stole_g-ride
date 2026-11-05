package com.gachiga.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.event.RideRequestExpired;
import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link QueueStatusEventListener} 검증. */
@ExtendWith(MockitoExtension.class)
class QueueStatusEventListenerTest {

    @Mock private QueueStatusPort queueStatusPort;
    @Mock private QueueStatusPusher pusher;

    private QueueStatusEventListener listener;

    @Nested
    @DisplayName("RideRequestCreated")
    class Created {

        @Test
        @DisplayName("QueueStatusPort 로 최신 상태를 조회해 push 한다")
        void pushesFreshStatus() {
            listener = new QueueStatusEventListener(queueStatusPort, pusher);
            QueueStatus status = new QueueStatus(10L, "WAITING", 300, 2);
            given(queueStatusPort.statusOf(1L)).willReturn(Optional.of(status));

            listener.on(new RideRequestCreated(10L, 1L, 2L));

            verify(pusher).push(1L, status);
        }

        @Test
        @DisplayName("조회에 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new QueueStatusEventListener(queueStatusPort, pusher);
            willThrow(new RuntimeException("DB 오류")).given(queueStatusPort).statusOf(1L);

            assertThatCode(() -> listener.on(new RideRequestCreated(10L, 1L, 2L)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("RideRequestCancelled")
    class Cancelled {

        @Test
        @DisplayName("조회 없이 CANCELLED 상태를 직접 만들어 push 한다")
        void pushesTerminalStatusDirectly() {
            listener = new QueueStatusEventListener(queueStatusPort, pusher);

            listener.on(new RideRequestCancelled(10L, 1L));

            verify(pusher).push(eq(1L), eq(new QueueStatus(10L, "CANCELLED", 0, 0)));
            verify(queueStatusPort, never()).statusOf(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("RideRequestExpired")
    class Expired {

        @Test
        @DisplayName("조회 없이 EXPIRED 상태를 직접 만들어 push 한다")
        void pushesTerminalStatusDirectly() {
            listener = new QueueStatusEventListener(queueStatusPort, pusher);

            listener.on(new RideRequestExpired(10L, 1L));

            verify(pusher).push(eq(1L), eq(new QueueStatus(10L, "EXPIRED", 0, 0)));
        }

        @Test
        @DisplayName("push가 실패해도 예외를 밖으로 내보내지 않는다 (CLAUDE.md §3)")
        void doesNotPropagateFailure() {
            listener = new QueueStatusEventListener(queueStatusPort, pusher);
            willThrow(new RuntimeException("연결 끊김"))
                    .given(pusher)
                    .push(eq(1L), org.mockito.ArgumentMatchers.any());

            assertThatCode(() -> listener.on(new RideRequestExpired(10L, 1L)))
                    .doesNotThrowAnyException();
        }
    }
}
