package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.event.RideRequestCancelled;
import com.gachiga.contract.event.RideRequestCreated;
import com.gachiga.contract.route.Coordinate;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import com.gachiga.contract.route.RouteProvider;
import com.gachiga.contract.route.RouteResult;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserStatus;
import com.gachiga.contract.user.UserSummary;
import com.gachiga.ride.dto.CreateRideRequestRequest;
import com.gachiga.ride.dto.RideRequestResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link RideRequestService} 생성 규칙 검증 (T1-9 항목 중 요청 1건 제한·500m 차단).
 *
 * <p>DB 없이 돈다 — 리포지토리와 계약 port 를 전부 Mock 으로 둔다 (CLAUDE.md §7).
 */
@ExtendWith(MockitoExtension.class)
class RideRequestServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long HUB_ID = 2L;

    /** 전남대 후문 */
    private static final HubInfo HUB = new HubInfo(HUB_ID, "전남대 후문", 35.1763d, 126.9123d, "CAMPUS");

    @Mock private RideRequestRepository rideRequestRepository;
    @Mock private HubPort hubPort;
    @Mock private UserPort userPort;
    @Mock private RouteProvider routeProvider;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RideRequestService service;

    @BeforeEach
    void setUp() {
        RideProperties properties = new RideProperties(List.of(5, 10, 15, 20), 10, 500, 30, 30);
        service =
                new RideRequestService(
                        rideRequestRepository,
                        properties,
                        hubPort,
                        userPort,
                        routeProvider,
                        eventPublisher);
    }

    /** 광주송정역 — 후문에서 약 10.6km */
    private CreateRideRequestRequest validRequest() {
        return new CreateRideRequestRequest(
                HUB_ID,
                "광주송정역",
                35.1378d,
                126.7902d,
                LocalDateTime.of(2026, 10, 20, 8, 30),
                10,
                true,
                new BigDecimal("0.20"));
    }

    private void givenActiveUser() {
        given(userPort.findById(USER_ID))
                .willReturn(
                        Optional.of(
                                new UserSummary(USER_ID, "후문호랑이", Gender.M, UserStatus.ACTIVE)));
    }

    private void givenHubExists() {
        given(hubPort.findById(HUB_ID)).willReturn(Optional.of(HUB));
    }

    private void givenRoute(int distance, int fare, boolean estimated) {
        given(routeProvider.findRoute(any(Coordinate.class), anyList(), any(Coordinate.class)))
                .willReturn(new RouteResult(fare, distance, 1_200, List.of(), estimated, null));
    }

    private void givenSaveEchoesBack() {
        given(rideRequestRepository.save(any(RideRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("정상 요청이면 대기 상태로 저장하고 생성 이벤트를 발행한다")
    void createsWaitingRequestAndPublishesEvent() {
        givenActiveUser();
        givenHubExists();
        givenRoute(10_591, 12_400, true);
        givenSaveEchoesBack();

        RideRequestResponse response = service.create(USER_ID, validRequest());

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.soloDistance()).isEqualTo(10_591);
        assertThat(response.soloFare()).isEqualTo(12_400);
        assertThat(response.estimated()).isTrue();
        assertThat(response.hub().name()).isEqualTo("전남대 후문");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(RideRequestCreated.class);
        assertThat(((RideRequestCreated) event.getValue()).userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("단독 요금은 요청 시점 값으로 고정된다 — 절감액의 기준선이다")
    void freezesSoloFareAtCreation() {
        givenActiveUser();
        givenHubExists();
        givenRoute(10_591, 12_400, false);
        givenSaveEchoesBack();

        ArgumentCaptor<RideRequest> saved = ArgumentCaptor.forClass(RideRequest.class);
        service.create(USER_ID, validRequest());

        verify(rideRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getSoloFare()).isEqualTo(12_400);
        assertThat(saved.getValue().isEstimated()).isFalse();
    }

    @Test
    @DisplayName("진행 중인 요청이 있으면 ALREADY_IN_QUEUE 로 거절한다 (1인 1건)")
    void rejectsSecondRequest() {
        givenActiveUser();
        given(rideRequestRepository.existsByUserIdAndStatusIn(eq(USER_ID), anyList()))
                .willReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_IN_QUEUE);

        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("거점에서 500m 미만이면 REQUEST_TOO_SHORT 로 거절한다 (E-08)")
    void rejectsTooShortTrip() {
        givenActiveUser();
        givenHubExists();

        // 후문에서 위도 0.0009도(약 100m)만 떨어진 지점
        CreateRideRequestRequest nearby =
                new CreateRideRequestRequest(
                        HUB_ID,
                        "바로 앞 편의점",
                        HUB.lat() + 0.0009d,
                        HUB.lng(),
                        LocalDateTime.of(2026, 10, 20, 8, 30),
                        10,
                        false,
                        new BigDecimal("0.20"));

        assertThatThrownBy(() -> service.create(USER_ID, nearby))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUEST_TOO_SHORT);

        verify(rideRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("거리 검사는 카카오를 부르기 전에 한다 — 걸러질 요청에 호출을 쓰지 않는다")
    void doesNotCallRouteProviderWhenTooShort() {
        givenActiveUser();
        givenHubExists();

        CreateRideRequestRequest nearby =
                new CreateRideRequestRequest(
                        HUB_ID,
                        "바로 앞",
                        HUB.lat(),
                        HUB.lng(),
                        LocalDateTime.of(2026, 10, 20, 8, 30),
                        10,
                        false,
                        new BigDecimal("0.20"));

        assertThatThrownBy(() -> service.create(USER_ID, nearby))
                .isInstanceOf(BusinessException.class);

        verify(routeProvider, never()).findRoute(any(), anyList(), any());
    }

    @Test
    @DisplayName("이용이 제한된 계정은 USER_SUSPENDED 로 거절한다")
    void rejectsSuspendedUser() {
        given(userPort.findById(USER_ID))
                .willReturn(
                        Optional.of(
                                new UserSummary(
                                        USER_ID, "정지된사용자", Gender.M, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.create(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_SUSPENDED);
    }

    @Test
    @DisplayName("없는 거점이면 NOT_FOUND 로 거절한다")
    void rejectsUnknownHub() {
        givenActiveUser();
        given(hubPort.findById(HUB_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("허용 목록에 없는 대기 시간은 거절한다 (FR-07)")
    void rejectsUnsupportedWaitMinutes() {
        CreateRideRequestRequest odd =
                new CreateRideRequestRequest(
                        HUB_ID,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        LocalDateTime.of(2026, 10, 20, 8, 30),
                        7,
                        false,
                        new BigDecimal("0.20"));

        assertThatThrownBy(() -> service.create(USER_ID, odd))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ── 조회·취소 (T1-3) ────────────────────────────────

    @Test
    @DisplayName("진행 중인 요청이 없으면 빈 값이다 — 404 가 아니다")
    void noRequestReturnsEmpty() {
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        eq(USER_ID), anyList()))
                .willReturn(Optional.empty());

        assertThat(service.findMyRequest(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 요청을 거점 정보·후보 수와 함께 돌려준다")
    void findsMyRequestWithHubAndCandidates() {
        givenHubExists();
        given(rideRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        eq(USER_ID), anyList()))
                .willReturn(Optional.of(savedWaitingRequest()));
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(2L);

        RideRequestResponse response = service.findMyRequest(USER_ID).orElseThrow();

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.hub().name()).isEqualTo("전남대 후문");
        assertThat(response.candidateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("본인 요청은 취소되고 취소 이벤트가 발행된다")
    void cancelsOwnRequest() {
        RideRequest request = savedWaitingRequest();
        given(rideRequestRepository.findById(10L)).willReturn(Optional.of(request));

        service.cancel(USER_ID, 10L);

        assertThat(request.getStatus()).isEqualTo(RideRequestStatus.CANCELLED);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(RideRequestCancelled.class);
    }

    @Test
    @DisplayName("남의 요청은 취소할 수 없다")
    void cannotCancelOthersRequest() {
        given(rideRequestRepository.findById(10L)).willReturn(Optional.of(savedWaitingRequest()));

        assertThatThrownBy(() -> service.cancel(999L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("없는 요청을 취소하면 NOT_FOUND")
    void cancelUnknownRequest() {
        given(rideRequestRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("이미 취소된 요청은 다시 취소할 수 없다")
    void cannotCancelTwice() {
        RideRequest request = savedWaitingRequest();
        request.cancel();
        given(rideRequestRepository.findById(10L)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 종료된 요청");
    }

    /** 저장된 것처럼 id 가 붙은 대기 요청 */
    private RideRequest savedWaitingRequest() {
        RideRequest request =
                RideRequest.create(
                        USER_ID,
                        HUB_ID,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        LocalDateTime.now().plusMinutes(5),
                        10,
                        true,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 10L);
        return request;
    }

    @Test
    @DisplayName("같은 거점 대기자 수에서 나 자신을 뺀다")
    void candidateCountExcludesSelf() {
        givenActiveUser();
        givenHubExists();
        givenRoute(10_591, 12_400, true);
        givenSaveEchoesBack();
        given(rideRequestRepository.countByHubIdAndStatus(HUB_ID, RideRequestStatus.WAITING))
                .willReturn(3L);

        RideRequestResponse response = service.create(USER_ID, validRequest());

        assertThat(response.candidateCount()).isEqualTo(2);
    }
}
