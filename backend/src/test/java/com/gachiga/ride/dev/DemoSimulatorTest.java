package com.gachiga.ride.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.util.GeoUtils;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import com.gachiga.ride.RideProperties;
import com.gachiga.ride.RideRequestService;
import com.gachiga.ride.dto.CreateRideRequestRequest;
import com.gachiga.ride.dto.RideRequestResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoSimulatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), SEOUL);
    private static final HubInfo HUB = new HubInfo(2L, "전남대 후문", 35.1795, 126.9120, "CAMPUS");

    private static final RideProperties RIDE =
            new RideProperties(
                    List.of(5, 10, 15, 20),
                    10,
                    List.of(new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.30")),
                    500,
                    30,
                    30);

    @Mock private RideRequestService rideRequestService;
    @Mock private HubPort hubPort;

    private final AtomicLong nextRequestId = new AtomicLong(100);

    private DemoSimulator simulator(int maxCount, int scanLimit) {
        return new DemoSimulator(
                rideRequestService,
                hubPort,
                RIDE,
                new SimulatorProperties(maxCount, scanLimit, 1500, 7000, 3),
                CLOCK);
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(hubPort.findById(2L)).thenReturn(Optional.of(HUB));
    }

    private void everyoneSucceeds() {
        when(rideRequestService.create(anyLong(), any()))
                .thenAnswer(inv -> response(nextRequestId.getAndIncrement(), inv.getArgument(0)));
    }

    @Test
    @DisplayName("사용자가 충분하면 요청한 건수만큼 넣고 그 뒤 사용자는 건드리지 않는다")
    void createsRequestedCount() {
        everyoneSucceeds();

        SimulationResult result = simulator(50, 200).simulate(2L, 3, 1, 42L, null);

        assertThat(result.created()).hasSize(3);
        assertThat(result.created()).extracting(SimulationResult.Created::userId).containsExactly(1L, 2L, 3L);
        assertThat(result.skipped()).isEmpty();
        assertThat(result.scannedFromUserId()).isEqualTo(1);
        assertThat(result.scannedToUserId()).isEqualTo(3);
        assertThat(result.note()).isNull();
        verify(rideRequestService, times(3)).create(anyLong(), any());
    }

    @Test
    @DisplayName("없는 사용자·이미 대기 중인 사용자는 건너뛰고 이유별로 센다")
    void skipsAndCountsByReason() {
        when(rideRequestService.create(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        when(rideRequestService.create(eq(2L), any()))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_IN_QUEUE));
        when(rideRequestService.create(eq(3L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        when(rideRequestService.create(eq(4L), any())).thenAnswer(inv -> response(10L, 4L));
        when(rideRequestService.create(eq(5L), any())).thenAnswer(inv -> response(11L, 5L));

        SimulationResult result = simulator(50, 200).simulate(2L, 2, 1, 42L, null);

        assertThat(result.created()).extracting(SimulationResult.Created::userId).containsExactly(4L, 5L);
        assertThat(result.skipped()).containsEntry("NOT_FOUND", 2).containsEntry("ALREADY_IN_QUEUE", 1);
        assertThat(result.scannedToUserId()).isEqualTo(5);
        assertThat(result.note()).isNull();
    }

    @Test
    @DisplayName("사용자가 모자라면 시도 상한에서 멈추고 안내를 붙인다")
    void stopsAtScanLimit() {
        when(rideRequestService.create(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        SimulationResult result = simulator(50, 5).simulate(2L, 3, 1, 42L, null);

        assertThat(result.created()).isEmpty();
        assertThat(result.skipped()).containsEntry("NOT_FOUND", 5);
        assertThat(result.scannedToUserId()).isEqualTo(5);
        assertThat(result.note()).contains("0건만").contains("1~5");
        verify(rideRequestService, times(5)).create(anyLong(), any());
    }

    @Test
    @DisplayName("건수가 1 미만이거나 상한을 넘으면 아무것도 넣지 않고 INVALID_INPUT")
    void rejectsCountOutOfRange() {
        DemoSimulator simulator = simulator(50, 200);

        assertThatThrownBy(() -> simulator.simulate(2L, 0, 1, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> simulator.simulate(2L, 51, 1, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(rideRequestService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("없는 거점이면 NOT_FOUND 이고 요청을 하나도 만들지 않는다")
    void rejectsUnknownHub() {
        when(hubPort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> simulator(50, 200).simulate(99L, 3, 1, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(rideRequestService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("가상 요청은 설정 범위 안의 거리·허용 옵션·10분 안의 출발 시각으로 만들어진다")
    void generatedCommandsAreValid() {
        everyoneSucceeds();
        ArgumentCaptor<CreateRideRequestRequest> captor =
                ArgumentCaptor.forClass(CreateRideRequestRequest.class);

        SimulationResult result = simulator(50, 200).simulate(2L, 50, 1, 7L, null);

        verify(rideRequestService, times(50)).create(anyLong(), captor.capture());
        LocalDateTime now = LocalDateTime.now(CLOCK);
        for (CreateRideRequestRequest command : captor.getAllValues()) {
            double meters =
                    GeoUtils.haversineMeters(HUB.lat(), HUB.lng(), command.destLat(), command.destLng());
            // 평면 근사라 하버사인과 1% 안쪽으로 어긋난다
            assertThat(meters).isBetween(1500 * 0.99, 7000 * 1.01);
            assertThat(meters).isGreaterThan(RIDE.minDistanceMeters());
            assertThat(command.hubId()).isEqualTo(HUB.id());
            assertThat(RIDE.allowedMaxWaitMinutes()).contains(command.maxWaitMin());
            assertThat(RIDE.allowedDetourRatios()).contains(command.maxDetourRatio());
            assertThat(command.departAt()).isBetween(now, now.plusMinutes(10));
            assertThat(command.destName()).startsWith("가상 목적지 ").endsWith("km)");
        }
        assertThat(result.created())
                .allSatisfy(c -> assertThat(c.distanceMeters()).isBetween(1500, 7000));
    }

    @Test
    @DisplayName("같은 seed 면 같은 목적지가 나온다 — 튜닝할 때 같은 조건을 다시 만들 수 있다")
    void sameSeedSameDestinations() {
        everyoneSucceeds();

        SimulationResult first = simulator(50, 200).simulate(2L, 10, 1, 123L, null);
        SimulationResult second = simulator(50, 200).simulate(2L, 10, 1, 123L, null);

        assertThat(second.created())
                .extracting(SimulationResult.Created::destName)
                .containsExactlyElementsOf(
                        first.created().stream().map(SimulationResult.Created::destName).toList());
    }

    @Test
    @DisplayName("호출한 사람(발표자)은 건너뛴다 — 발표자에게 가상 요청이 들어가면 직접 요청할 때 409 가 난다")
    void skipsCaller() {
        everyoneSucceeds();

        SimulationResult result = simulator(50, 200).simulate(2L, 3, 1, 42L, 1L);

        assertThat(result.created()).extracting(SimulationResult.Created::userId).containsExactly(2L, 3L, 4L);
        assertThat(result.skipped()).containsEntry(DemoSimulator.SKIPPED_CALLER, 1);
        verify(rideRequestService, never()).create(eq(1L), any());
    }

    @Test
    @DisplayName("같은 seed 면 건너뛴 사람이 달라도 n번째 요청의 목적지가 같다")
    void seedIsStableAcrossSkips() {
        everyoneSucceeds();
        SimulationResult first = simulator(50, 200).simulate(2L, 3, 1, 99L, null);

        // 두 번째에는 1번이 이미 대기 중이라 건너뛴다
        org.mockito.Mockito.reset(rideRequestService);
        when(rideRequestService.create(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_IN_QUEUE));
        when(rideRequestService.create(org.mockito.ArgumentMatchers.longThat(id -> id > 1), any()))
                .thenAnswer(inv -> response(nextRequestId.getAndIncrement(), inv.getArgument(0)));
        SimulationResult second = simulator(50, 200).simulate(2L, 3, 1, 99L, null);

        assertThat(second.created()).extracting(SimulationResult.Created::userId).containsExactly(2L, 3L, 4L);
        assertThat(second.created())
                .extracting(SimulationResult.Created::destName)
                .containsExactlyElementsOf(
                        first.created().stream().map(SimulationResult.Created::destName).toList());
    }

    private static RideRequestResponse response(Long requestId, Long userId) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        return new RideRequestResponse(
                requestId,
                userId,
                null,
                "가상",
                0,
                0,
                now,
                now.plusMinutes(10),
                10,
                false,
                new BigDecimal("0.20"),
                0,
                0,
                "WAITING",
                600,
                0,
                true,
                null,
                now);
    }
}
