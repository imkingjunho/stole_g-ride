package com.gachiga.ride;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.route.*;
import com.gachiga.contract.user.*;
import com.gachiga.matching.*;
import com.gachiga.matching.algorithm.CombinationEvaluator;
import com.gachiga.matching.domain.*;
import com.gachiga.matching.port.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 조건부 UPDATE와 커밋 후 계약 이벤트를 함께 검증한다. 외부 API/Redis 연결 없음. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    GroupService.class,
    RideRequestAdapter.class,
    RideGroupEventListener.class,
    RideCandidateCounter.class,
    MatchingLifecycleTest.Config.class
})
class MatchingLifecycleTest {
    static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Autowired RideRequestRepository requests;
    @Autowired RideRequestAdapter ride;
    @Autowired MatchGroupRepository groups;
    @Autowired MatchMemberRepository members;
    @Autowired GroupService service;
    @MockBean UserPort users;
    RouteProvider routes;
    HubPort hubs;
    CombinationEvaluator evaluator;

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return CLOCK;
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        RideProperties rideProperties() {
            return new RideProperties(
                    List.of(10), 10, List.of(new BigDecimal("0.30")), 500, 30, 30);
        }
    }

    @BeforeEach
    void setup() {
        members.deleteAll();
        groups.deleteAll();
        requests.deleteAll();
        when(users.findById(anyLong()))
                .thenAnswer(
                        i ->
                                Optional.of(
                                        new UserSummary(
                                                i.getArgument(0),
                                                "학우",
                                                Gender.M,
                                                UserStatus.ACTIVE)));
        routes = mock(RouteProvider.class);
        hubs = mock(HubPort.class);
        when(hubs.findById(2L))
                .thenReturn(Optional.of(new HubInfo(2L, "전남대 후문", 35.17, 126.90, "CAMPUS")));
        when(routes.findRoute(any(), anyList(), any()))
                .thenReturn(
                        new RouteResult(
                                7000,
                                2000,
                                300,
                                List.of(
                                        new RouteResult.Section(
                                                1000, 150, List.of(new Coordinate(35.17, 126.91))),
                                        new RouteResult.Section(
                                                1000, 150, List.of(new Coordinate(35.17, 126.92)))),
                                true,
                                null));
        evaluator = new CombinationEvaluator(routes, hubs, users, CLOCK);
    }

    private RideRequest add(long user, double lng, int distance) {
        return requests.saveAndFlush(
                RideRequest.create(
                        user,
                        2L,
                        "목적지 " + user,
                        35.17,
                        lng,
                        NOW.plusMinutes(1),
                        10,
                        false,
                        new BigDecimal("0.30"),
                        distance,
                        6000,
                        true,
                        NOW));
    }

    private MatchCandidate candidate() {
        add(1, 126.91, 1000);
        add(2, 126.92, 2000);
        return evaluator.evaluate(ride.findWaiting());
    }

    @Test
    void repeatedTicksCreateOneGroupAndPersistCompleteResponse() {
        add(1, 126.91, 1000);
        add(2, 126.92, 2000);
        var engine = new MatchingEngine(ride, evaluator, service);
        engine.executeTick();
        engine.executeTick();
        assertThat(groups.count()).isEqualTo(1);
        Long id = groups.findAll().get(0).getId();
        assertThat(requests.findAll())
                .allSatisfy(
                        r -> {
                            assertThat(r.getStatus()).isEqualTo(RideRequestStatus.CONFIRMED);
                            assertThat(r.getGroupId()).isEqualTo(id);
                        });
        var detail = service.detail(id, 1L);
        assertThat(detail.fareMethod()).isEqualTo("DISTANCE_RATIO");
        assertThat(detail.members().stream().mapToInt(m -> m.shareAmount()).sum())
                .isEqualTo(detail.totalFare());
        assertThat(detail.members()).filteredOn(m -> m.isMe()).hasSize(1);
        assertThat(detail.members()).extracting(m -> m.soloDistance()).containsExactly(1000, 2000);
        assertThat(detail.route().sections()).hasSize(2);
        verify(routes, times(1))
                .findRoute(
                        eq(new Coordinate(35.17, 126.90)),
                        eq(List.of(new Coordinate(35.17, 126.91))),
                        eq(new Coordinate(35.17, 126.92)));
        assertThatThrownBy(() -> service.detail(id, 99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GROUP_NOT_MEMBER));
        assertThatThrownBy(() -> service.complete(id, 99L)).isInstanceOf(BusinessException.class);
        service.complete(id, 1L);
        assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo(GroupStatus.CONFIRMED);
        service.complete(id, 2L);
        service.complete(id, 2L);
        assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo(GroupStatus.COMPLETED);
        assertThat(requests.findAll())
                .allSatisfy(
                        r -> {
                            assertThat(r.getStatus()).isEqualTo(RideRequestStatus.COMPLETED);
                            assertThat(r.getActiveUserId()).isNull();
                        });
    }

    @Test
    void losingSecondClaimRollsBackFirstClaimAndCreatesNoGroup() {
        var c = candidate();
        var second = requests.findById(c.riders().get(1).requestId()).orElseThrow();
        second.cancel();
        requests.saveAndFlush(second);
        assertThat(service.create(c)).isNull();
        assertThat(groups.count()).isZero();
        assertThat(members.count()).isZero();
        assertThat(requests.findById(c.riders().get(0).requestId()).orElseThrow().getStatus())
                .isEqualTo(RideRequestStatus.WAITING);
    }

    @Test
    void concurrentTicksCannotClaimTheSameRequestsTwice() throws Exception {
        var c = candidate();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Long> work =
                    () -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("시작 신호 없음");
                        return service.create(c);
                    };
            var first = pool.submit(work);
            var second = pool.submit(work);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results =
                    Arrays.asList(
                            first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Objects::nonNull)).hasSize(1);
            assertThat(groups.count()).isEqualTo(1);
            assertThat(members.count()).isEqualTo(2);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void httpContractHasEnvelopeAndUsesAuthenticatedMembership() throws Exception {
        Long id = service.create(candidate());
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new com.gachiga.matching.api.GroupController(service))
                        .setCustomArgumentResolvers(new com.gachiga.auth.JwtCurrentUserResolver())
                        .setControllerAdvice(
                                new com.gachiga.common.exception.GlobalExceptionHandler())
                        .build();
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            context.setAuthentication(
                    new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(1L, null, List.of()));
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                    "/api/groups/" + id))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isOk())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.success")
                                    .value(true))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.data.groupId")
                                    .value(id.intValue()))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.data.fareMethod")
                                    .value("DISTANCE_RATIO"))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.data.members[0].soloDistance")
                                    .value(1000))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.data.members[0].isMe")
                                    .value(true));
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post("/api/groups/" + id + "/complete"))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isOk())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.success")
                                    .value(true))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.data")
                                    .value(org.hamcrest.Matchers.nullValue()));
            context.setAuthentication(
                    new org.springframework.security.authentication
                            .UsernamePasswordAuthenticationToken(99L, null, List.of()));
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                    "/api/groups/" + id))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isForbidden())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .jsonPath("$.error.code")
                                    .value("GROUP_NOT_MEMBER"));
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                    "/api/groups/999999"))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isNotFound());
            context.setAuthentication(null);
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                    "/api/groups/" + id))
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .status()
                                    .isUnauthorized());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void mergedStopKeepsSeparateDistanceWeights() {
        add(1, 126.91, 1000);
        add(2, 126.9101, 2000);
        when(routes.findRoute(any(), anyList(), any()))
                .thenReturn(
                        new RouteResult(
                                7000,
                                1000,
                                150,
                                List.of(new RouteResult.Section(1000, 150, List.of())),
                                true,
                                null));
        var c = evaluator.evaluate(ride.findWaiting());
        assertThat(c.dropoffOrders()).isEqualTo(Map.of(1L, 1, 2L, 1));
        assertThat(c.shares()).isEqualTo(Map.of(1L, 2333, 2L, 4667));
        verify(routes).findRoute(any(), eq(List.of()), any());
    }

    @Test
    void legacyGroupWithoutSnapshotIsExplicitlyRejected() {
        var group = groups.saveAndFlush(MatchGroup.builder().hubId(2L).totalFare(7000).build());
        members.saveAndFlush(
                MatchMember.builder()
                        .groupId(group.getId())
                        .userId(1L)
                        .requestId(1L)
                        .boardingOrder(1)
                        .dropoffOrder(1)
                        .build());
        assertThatThrownBy(() -> service.detail(group.getId(), 1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void personalDetourLimitRejectsUnacceptableRoute() {
        add(1, 126.91, 500);
        add(2, 126.92, 2000);
        assertThat(evaluator.evaluate(ride.findWaiting())).isNull();
        assertThat(groups.count()).isZero();
    }
}
