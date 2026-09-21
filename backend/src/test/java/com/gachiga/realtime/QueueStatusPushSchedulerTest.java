package com.gachiga.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

/**
 * {@link QueueStatusPushScheduler} 검증. STOMP 브로커 없이 도는 순수 로직 테스트다 (CLAUDE.md §7).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueStatusPushSchedulerTest {

    @Mock private SimpUserRegistry simpUserRegistry;
    @Mock private QueueStatusPort queueStatusPort;
    @Mock private QueueStatusPusher pusher;
    @Mock private SimpUser userOne;
    @Mock private SimpUser userTwo;

    private QueueStatusPushScheduler scheduler;

    @Test
    @DisplayName("연결된 사용자 중 진행 중인 요청이 있는 사람에게만 push 한다")
    void pushesOnlyUsersWithActiveRequest() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("1");
        given(userTwo.getName()).willReturn("2");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne, userTwo));

        QueueStatus status = new QueueStatus(10L, "WAITING", 300, 1);
        given(queueStatusPort.statusOf(1L)).willReturn(Optional.of(status));
        given(queueStatusPort.statusOf(2L)).willReturn(Optional.empty());

        scheduler.pushToConnectedUsers();

        verify(pusher).push(1L, status);
        verify(pusher, never()).push(eq(2L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("사용자 id 형식이 아니면 건너뛴다")
    void skipsNonNumericName() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("not-a-number");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne));

        scheduler.pushToConnectedUsers();

        verify(pusher, never()).push(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("한 명 조회가 실패해도 나머지는 처리한다")
    void toleratesPerUserFailure() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("1");
        given(userTwo.getName()).willReturn("2");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne, userTwo));

        given(queueStatusPort.statusOf(1L)).willThrow(new RuntimeException("DB 오류"));
        QueueStatus status = new QueueStatus(20L, "WAITING", 100, 0);
        given(queueStatusPort.statusOf(2L)).willReturn(Optional.of(status));

        scheduler.pushToConnectedUsers();

        verify(pusher).push(2L, status);
    }
}
