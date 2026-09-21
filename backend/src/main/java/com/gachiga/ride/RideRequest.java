package com.gachiga.ride;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 요청 한 건 (PRD §7.1 {@code ride_requests}).
 *
 * <p><b>다른 모듈의 엔티티를 참조하지 않는다.</b> 사용자와 거점은 {@code Long} id 로만 들고 있고,
 * 정보가 필요하면 {@code contract} 의 {@code UserPort}·{@code HubPort} 로 조회한다 (PRD §7.2).
 * 그래야 임승현·송준호가 엔티티를 아직 안 만들었어도 이 코드가 컴파일된다.
 *
 * <p>상태는 {@code setStatus()} 같은 setter 없이 의도가 드러나는 메서드로만 바꾼다
 * ({@link #markMatched}, {@link #cancel} 등). 바깥에서 아무 상태로나 덮어쓰지 못하게 하려는 것이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "ride_requests",
        indexes = {
            // 매칭 tick 이 "거점별 대기 중 요청을 출발 시각 순으로" 훑는다 (PRD §7.1)
            @Index(name = "idx_ride_requests_status_hub_depart", columnList = "status, hub_id, depart_at"),
            // 1인 1건 제한과 내 요청 조회
            @Index(name = "idx_ride_requests_user_status", columnList = "user_id, status")
        })
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 요청한 사용자 id. {@code users.id} 를 값으로만 들고 있다 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 출발 거점 id. {@code hubs.id} 를 값으로만 들고 있다 */
    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "dest_name", nullable = false, length = 100)
    private String destName;

    @Column(name = "dest_lat", nullable = false)
    private double destLat;

    @Column(name = "dest_lng", nullable = false)
    private double destLng;

    /** 희망 출발 시각. 대기열 정렬 기준이다 */
    @Column(name = "depart_at", nullable = false)
    private LocalDateTime departAt;

    /** 최대 대기 시간(분). 5·10·15·20 중 하나 (FR-07) */
    @Column(name = "max_wait_min", nullable = false)
    private int maxWaitMin;

    /** true 면 동성끼리만. 그룹에 한 명이라도 true 면 전원 동성이어야 한다 (E-06) */
    @Column(name = "same_gender_only", nullable = false)
    private boolean sameGenderOnly;

    /** 허용 최대 우회 비율. 0.10 / 0.20 / 0.30 */
    @Column(name = "max_detour_ratio", nullable = false, precision = 3, scale = 2)
    private BigDecimal maxDetourRatio;

    /** 혼자 갈 때의 거리(m). 생성 시점에 계산해 고정한다 */
    @Column(name = "solo_distance", nullable = false)
    private int soloDistance;

    /** 혼자 갈 때의 요금(원). 절감액의 기준선이라 나중에 바꾸지 않는다 */
    @Column(name = "solo_fare", nullable = false)
    private int soloFare;

    /**
     * {@link #soloFare} 가 카카오 실호출이 아니라 직선거리 추정치인지 (E-03).
     *
     * <p>화면에 "추정치" 배지를 띄울지 판단하는 값이라 요청과 함께 저장한다.
     * 생성 시점의 판단을 그대로 보존해야 나중에 조회해도 같은 표시가 나온다.
     */
    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RideRequestStatus status;

    /**
     * 낙관적 락 버전 (E-02).
     *
     * <p>두 그룹이 같은 요청을 동시에 집어 가려 할 때 나중 쪽이 실패하도록 만드는 장치다.
     * JPA 가 자동으로 관리하므로 코드에서 직접 올리지 않는다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 이 시각이 지나면 만료된다. {@code createdAt + maxWaitMin} */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 새 요청을 만든다. 상태는 {@code WAITING} 으로 시작한다.
     *
     * <p>시각을 인자로 받는 이유는 테스트에서 "지금"을 고정할 수 있게 하기 위해서다.
     *
     * @param now 생성 시각. {@code expiresAt} 도 여기서부터 센다
     */
    public static RideRequest create(
            Long userId,
            Long hubId,
            String destName,
            double destLat,
            double destLng,
            LocalDateTime departAt,
            int maxWaitMin,
            boolean sameGenderOnly,
            BigDecimal maxDetourRatio,
            int soloDistance,
            int soloFare,
            boolean estimated,
            LocalDateTime now) {

        RideRequest request = new RideRequest();
        request.userId = userId;
        request.hubId = hubId;
        request.destName = destName;
        request.destLat = destLat;
        request.destLng = destLng;
        request.departAt = departAt;
        request.maxWaitMin = maxWaitMin;
        request.sameGenderOnly = sameGenderOnly;
        request.maxDetourRatio = maxDetourRatio;
        request.soloDistance = soloDistance;
        request.soloFare = soloFare;
        request.estimated = estimated;
        request.status = RideRequestStatus.WAITING;
        request.createdAt = now;
        request.expiresAt = now.plusMinutes(maxWaitMin);
        return request;
    }

    // ── 상태 전이 ────────────────────────────────────────────

    /** 그룹에 배정됐다. 대기 중일 때만 가능하다 */
    public void markMatched() {
        requireStatus(RideRequestStatus.WAITING);
        this.status = RideRequestStatus.MATCHED;
    }

    /** 그룹이 해체돼 대기열로 돌아왔다 (E-01). 남은 시간({@code expiresAt})은 그대로 둔다 */
    public void markWaiting() {
        this.status = RideRequestStatus.WAITING;
    }

    /** 그룹이 확정됐다 */
    public void markConfirmed() {
        this.status = RideRequestStatus.CONFIRMED;
    }

    /** 탑승이 끝났다 */
    public void markCompleted() {
        this.status = RideRequestStatus.COMPLETED;
    }

    /**
     * 사용자가 취소했다. 아직 진행 중일 때만 가능하다.
     *
     * <p>이미 끝난 요청을 다시 취소하려 하면 {@code INVALID_INPUT} 이 난다.
     */
    public void cancel() {
        if (status.isFinished()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "이미 종료된 요청은 취소할 수 없습니다.");
        }
        this.status = RideRequestStatus.CANCELLED;
    }

    /** 대기 시간이 지나 만료됐다 (FR-10). 스케줄러가 호출한다 */
    public void expire() {
        this.status = RideRequestStatus.EXPIRED;
    }

    // ── 조회용 ──────────────────────────────────────────────

    /** 주어진 시각 기준으로 만료됐는지 */
    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 만료까지 남은 시간(초). 이미 지났으면 0.
     *
     * <p>대기 화면의 카운트다운에 쓴다 (FR-09).
     */
    public int remainingSeconds(LocalDateTime now) {
        long seconds = Duration.between(now, expiresAt).toSeconds();
        return (int) Math.max(0L, seconds);
    }

    /** 본인 요청인지. 남의 요청을 취소하지 못하게 막는 데 쓴다 */
    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    private void requireStatus(RideRequestStatus expected) {
        if (status != expected) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "요청 상태가 " + expected + " 가 아니라 " + status + " 입니다.");
        }
    }
}
