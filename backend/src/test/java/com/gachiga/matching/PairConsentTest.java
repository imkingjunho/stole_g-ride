package com.gachiga.matching;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.event.GroupConfirmed;
import com.gachiga.contract.event.GroupDissolved;
import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.route.RouteResult;
import com.gachiga.contract.user.UserPort;
import com.gachiga.matching.domain.*;
import com.gachiga.matching.port.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

class PairConsentTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-11-20T07:05:00Z"), ZoneOffset.UTC);
    final MatchGroupRepository groups = mock(MatchGroupRepository.class);
    final MatchMemberRepository members = mock(MatchMemberRepository.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final RideRequestPort requests = mock(RideRequestPort.class);
    final ObjectMapper mapper = new ObjectMapper();
    final GroupService service = new GroupService(requests, mock(UserPort.class), groups, members, events,
            mapper, clock, mock(PlatformTransactionManager.class));
    final MatchGroup group = MatchGroup.builder().id(7L).hubId(1L).hubLat(35.1).hubLng(126.9)
            .pairOfferKey("11:12").status(GroupStatus.PENDING).acceptDeadline(LocalDateTime.now(clock).plusSeconds(60)).build();

    @BeforeEach void prepare() throws Exception {
        group.setRouteJson(mapper.writeValueAsString(new RouteResult(7000, 3000, 400, List.of(), true, null)));
        when(groups.findLockedById(7L)).thenReturn(Optional.of(group));
        when(groups.findById(7L)).thenReturn(Optional.of(group));
        when(members.findByGroupId(7L)).thenReturn(List.of(member(11L), member(12L)));
    }

    MatchMember member(long id) {
        return MatchMember.builder().userId(id).requestId(id).destName("목적지").destLat(35.2)
                .destLng(126.9).boardingOrder(1).dropoffOrder(1).soloDistance(3000).soloFare(5000).build();
    }

    @Test void requiresTwoDistinctAcceptancesAndConfirmsOnce() {
        assertThat(service.accept(7L, 11L).status()).isEqualTo("PENDING");
        service.accept(7L, 11L);
        verifyNoInteractions(events);
        assertThat(service.accept(7L, 12L).status()).isEqualTo("CONFIRMED");
        service.accept(7L, 12L);
        verify(events).publishEvent(new GroupConfirmed(7L, List.of(11L, 12L)));
        verifyNoMoreInteractions(events);
    }

    @Test void declineReturnsBothRequestsToWaitingWithoutConfirming() {
        service.accept(7L, 11L);
        assertThatThrownBy(() -> service.rejectOffer(7L, 23L)).isInstanceOf(BusinessException.class);
        service.rejectOffer(7L, 12L);
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DISSOLVED);
        verify(requests).markWaiting(11L);
        verify(requests).markWaiting(12L);
        verify(events).publishEvent(new GroupDissolved(7L, List.of(11L, 12L), "PAIR_DECLINED"));
    }

    @Test void deadlineRejectsLateConsentAndReleasesUnansweredRequests() {
        group.setAcceptDeadline(LocalDateTime.now(clock));
        assertThatThrownBy(() -> service.accept(7L, 11L)).isInstanceOf(BusinessException.class);
        service.expireOffer(7L);
        service.expireOffer(7L);
        verify(events).publishEvent(new GroupDissolved(7L, List.of(11L, 12L), "PAIR_EXPIRED"));
        verifyNoMoreInteractions(events);
    }
}
