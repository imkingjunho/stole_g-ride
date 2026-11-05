package com.gachiga.ride.dev;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import com.gachiga.ride.RideProperties;
import com.gachiga.ride.RideRequestService;
import com.gachiga.ride.dto.CreateRideRequestRequest;
import com.gachiga.ride.dto.RideRequestResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 데모 시뮬레이터 (T2-1). 가상 매칭 요청을 한 번에 여러 건 넣는다 — 시연과 매칭 튜닝용.
 *
 * <p><b>local 프로파일에서만 뜬다.</b> 운영·테스트 컨텍스트에는 빈 자체가 없다.
 *
 * <p>요청은 {@link RideRequestService#create} 를 그대로 거친다. 실제 사용자가 누른 것과 같은 검증
 * (ACTIVE 사용자, 1인 1건, 거점 존재, 최소 거리)을 통과한 요청만 들어가므로, 시뮬레이터 때문에
 * 대기열이 실제로는 생길 수 없는 상태가 되지 않는다.
 *
 * <p>사용자 목록을 주는 port 가 없어서 id 를 {@code fromUserId} 부터 하나씩 시도한다. 없는 사용자,
 * 이용 제한 사용자, 이미 대기 중인 사용자는 건너뛰고 이유별로 센다. <b>호출한 사람(발표자)은 건너뛴다</b> —
 * 발표자 계정에 가상 요청이 들어가면 발표자가 직접 요청할 때 {@code ALREADY_IN_QUEUE} 가 난다.
 *
 * <p>{@code seed} 가 같으면 <b>n번째로 들어간 요청</b>의 목적지·옵션이 같다. 목적지를 먼저 뽑아 두고 성공할
 * 때마다 하나씩 쓰므로, 누가 이미 대기 중이라 건너뛰었는지와 상관없다. 어느 사용자에게 들어가는지와
 * 출발 시각의 기준(지금)은 달라질 수 있다.
 *
 * <p>목적지는 거점을 중심으로 몇 갈래 방향에 몰아서 뿌린다. 사방으로 고르게 뿌리면 경로가 겹치는
 * 조합이 거의 안 생겨 매칭 시연이 안 되기 때문이다.
 */
@Slf4j
@Profile("local")
@Service
@RequiredArgsConstructor
public class DemoSimulator {

    /** 위도 1도의 길이(m). 거점 주변 수 km 안에서는 평면 근사로 충분하다 */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    /** 갈래 방향에서 좌우로 흔드는 각도(도) */
    private static final double CORRIDOR_JITTER_DEGREES = 15.0;

    /** 희망 출발 시각을 지금부터 최대 몇 분 뒤까지 흩뿌릴지 */
    private static final int MAX_DEPART_DELAY_MINUTES = 10;

    /** 동성만 옵션을 켜는 비율. 전부 켜면 E-06 필터 때문에 매칭이 거의 안 난다 */
    private static final double SAME_GENDER_ONLY_RATE = 0.2;

    private static final String[] DIRECTIONS = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};

    /** 건너뛴 이유 — 호출한 사람 자신. 에러 코드가 아니라 시뮬레이터가 정한 이름이다 */
    static final String SKIPPED_CALLER = "CALLER";

    private final RideRequestService rideRequestService;
    private final HubPort hubPort;
    private final RideProperties rideProperties;
    private final SimulatorProperties simulatorProperties;
    private final Clock clock;

    /**
     * 가상 요청을 넣는다.
     *
     * @param hubId      출발 거점 id
     * @param count      넣을 건수. 1 이상 {@code maxCount} 이하
     * @param fromUserId 이 id 부터 사용자를 시도한다
     * @param seed       난수 씨앗. 같은 값이면 같은 목적지가 나온다. null 이면 매번 다르다
     * @param callerId   호출한 사람. 이 사용자는 건너뛴다. null 이면 건너뛰지 않는다
     */
    public SimulationResult simulate(
            Long hubId, int count, long fromUserId, Long seed, Long callerId) {
        if (count < 1 || count > simulatorProperties.maxCount()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "한 번에 1~" + simulatorProperties.maxCount() + "건까지 넣을 수 있습니다.");
        }
        if (fromUserId < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자 id 는 1 이상이어야 합니다.");
        }
        HubInfo hub =
                hubPort.findById(hubId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.NOT_FOUND, "출발 거점을 찾을 수 없습니다."));

        // 목적지·옵션을 먼저 다 뽑아 둔다. 건너뛴 사용자가 난수를 쓰면 같은 seed 라도 결과가 달라진다
        Random random = seed == null ? new Random() : new Random(seed);
        double[] corridors = corridorBearings(random);
        List<Planned> plan = new ArrayList<>(count);
        for (int seq = 1; seq <= count; seq++) {
            Destination destination = randomDestination(hub, random, corridors, seq);
            plan.add(new Planned(destination, command(hub, destination, random)));
        }

        List<SimulationResult.Created> created = new ArrayList<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        long lastUserId = fromUserId + simulatorProperties.userScanLimit() - 1;
        long userId = fromUserId;
        for (; userId <= lastUserId && created.size() < count; userId++) {
            if (callerId != null && callerId == userId) {
                skipped.merge(SKIPPED_CALLER, 1, Integer::sum);
                continue;
            }
            Planned next = plan.get(created.size());
            try {
                RideRequestResponse response = rideRequestService.create(userId, next.command());
                created.add(
                        new SimulationResult.Created(
                                userId,
                                response.requestId(),
                                next.destination().name(),
                                next.destination().meters()));
            } catch (BusinessException e) {
                skipped.merge(e.getErrorCode().name(), 1, Integer::sum);
            }
        }
        long scannedTo = userId - 1;

        String note = null;
        if (created.size() < count) {
            note =
                    "요청 "
                            + count
                            + "건 중 "
                            + created.size()
                            + "건만 넣었습니다. 사용자 id "
                            + fromUserId
                            + "~"
                            + scannedTo
                            + " 에 대기 중이 아닌 ACTIVE 사용자가 모자랍니다.";
        }
        log.info(
                "시뮬레이터 hubId={} 요청 {}건 → {}건 투입, 건너뜀 {}, 사용자 id {}~{}",
                hubId,
                count,
                created.size(),
                skipped,
                fromUserId,
                scannedTo);
        return new SimulationResult(count, created, skipped, fromUserId, scannedTo, note);
    }

    private double[] corridorBearings(Random random) {
        double[] bearings = new double[Math.max(1, simulatorProperties.corridors())];
        for (int i = 0; i < bearings.length; i++) {
            bearings[i] = random.nextDouble() * 360.0;
        }
        return bearings;
    }

    private Destination randomDestination(HubInfo hub, Random random, double[] corridors, int seq) {
        double jitter = (random.nextDouble() * 2 - 1) * CORRIDOR_JITTER_DEGREES;
        double bearing = normalize(corridors[random.nextInt(corridors.length)] + jitter);
        int min = simulatorProperties.minDistanceMeters();
        int max = simulatorProperties.maxDistanceMeters();
        double meters = min + random.nextDouble() * (max - min);

        double radians = Math.toRadians(bearing);
        double lat = hub.lat() + meters * Math.cos(radians) / METERS_PER_DEGREE_LAT;
        double lng =
                hub.lng()
                        + meters
                                * Math.sin(radians)
                                / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(hub.lat())));

        String name =
                "가상 목적지 "
                        + seq
                        + " ("
                        + direction(bearing)
                        + " "
                        + String.format(Locale.ROOT, "%.1f", meters / 1000)
                        + "km)";
        return new Destination(name, lat, lng, (int) Math.round(meters));
    }

    private CreateRideRequestRequest command(HubInfo hub, Destination destination, Random random) {
        LocalDateTime departAt =
                LocalDateTime.now(clock)
                        .plusMinutes(random.nextInt(MAX_DEPART_DELAY_MINUTES + 1))
                        .withNano(0);
        List<Integer> waits = rideProperties.allowedMaxWaitMinutes();
        List<BigDecimal> detours = rideProperties.allowedDetourRatios();
        return new CreateRideRequestRequest(
                hub.id(),
                destination.name(),
                destination.lat(),
                destination.lng(),
                departAt,
                waits.get(random.nextInt(waits.size())),
                random.nextDouble() < SAME_GENDER_ONLY_RATE,
                detours.get(random.nextInt(detours.size())));
    }

    private static double normalize(double bearing) {
        double b = bearing % 360.0;
        return b < 0 ? b + 360.0 : b;
    }

    private static String direction(double bearing) {
        return DIRECTIONS[(int) Math.round(bearing / 45.0) % DIRECTIONS.length];
    }

    private record Destination(String name, double lat, double lng, int meters) {}

    private record Planned(Destination destination, CreateRideRequestRequest command) {}
}
