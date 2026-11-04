package com.gachiga.realtime;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import java.util.Map;
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
    @DisplayName("연결된 사용자를 한 번에 조회해, 진행 중인 요청이 있는 사람에게만 push 한다")
    void pushesOnlyUsersWithActiveRequest() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("1");
        given(userTwo.getName()).willReturn("2");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne, userTwo));

        QueueStatus status = new QueueStatus(10L, "WAITING", 300, 1);
        given(queueStatusPort.statusOfAll(anyCollection())).willReturn(Map.of(1L, status));

        scheduler.pushToConnectedUsers();

        verify(pusher).push(1L, status);
        verify(pusher, never()).push(2L, status);
    }

    @Test
    @DisplayName("사용자 id 형식이 아니면 조회 대상에서 빠진다")
    void skipsNonNumericName() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("not-a-number");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne));

        scheduler.pushToConnectedUsers();

        verify(queueStatusPort, never()).statusOfAll(anyCollection());
        verify(pusher, never()).push(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("연결된 사용자가 없으면 조회하지 않는다")
    void skipsWhenNoOneConnected() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(simpUserRegistry.getUsers()).willReturn(Set.of());

        scheduler.pushToConnectedUsers();

        verify(queueStatusPort, never()).statusOfAll(anyCollection());
    }

    @Test
    @DisplayName("배치 조회가 실패해도 스케줄러가 죽지 않는다")
    void toleratesBatchFailure() {
        scheduler = new QueueStatusPushScheduler(simpUserRegistry, queueStatusPort, pusher);
        given(userOne.getName()).willReturn("1");
        given(simpUserRegistry.getUsers()).willReturn(Set.of(userOne));
        given(queueStatusPort.statusOfAll(anyCollection())).willThrow(new RuntimeException("DB 오류"));

        scheduler.pushToConnectedUsers();

        verify(pusher, never()).push(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
