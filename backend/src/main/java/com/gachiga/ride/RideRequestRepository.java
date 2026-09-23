package com.gachiga.ride;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link RideRequest} 조회·저장.
 *
 * <p>상태 전이는 엔티티의 메서드로 하고, 여기서는 찾아 오기만 한다.
 * 낙관적 락이 걸린 갱신({@code tryMarkMatched})은 T1-5 에서 추가한다.
 */
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    /**
     * 사용자에게 진행 중인 요청이 있는지. 1인 1건 제한 검사에 쓴다 (FR-08).
     *
     * @param statuses 보통 {@code WAITING·MATCHED·CONFIRMED}
     */
    boolean existsByUserIdAndStatusIn(Long userId, Collection<RideRequestStatus> statuses);

    /**
     * 사용자의 진행 중인 요청. 1인 1건 제한이 있으므로 최대 한 건이다.
     *
     * <p>대기 화면과 {@code QueueStatusPort} 가 쓴다.
     */
    Optional<RideRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            Long userId, Collection<RideRequestStatus> statuses);

    /**
     * 지금 매칭 대상이 되는 요청들.
     *
     * <p>곧 만료될 요청을 새 그룹에 넣으면 성사 직후 만료되므로, 호출하는 쪽이
     * {@code now + 30초} 를 넘겨 여유가 있는 것만 받는다 (E-04).
     *
     * @param cutoff 이 시각보다 늦게 만료되는 것만 포함한다
     */
    List<RideRequest> findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
            RideRequestStatus status, LocalDateTime cutoff);

    /**
     * 만료 처리 대상. 대기 중인데 만료 시각이 지난 것들 (FR-10).
     *
     * @param threshold 보통 "지금". 이 시각 이전에 만료되는 것을 고른다
     */
    List<RideRequest> findByStatusAndExpiresAtLessThanEqual(
            RideRequestStatus status, LocalDateTime threshold);

    /**
     * 같은 거점에서 대기 중인 요청 수. 대기 화면의 "함께 기다리는 사람" 수에 쓴다 (FR-09).
     *
     * <p>자기 자신이 포함된 값이므로 호출하는 쪽에서 1을 뺀다.
     */
    long countByHubIdAndStatus(Long hubId, RideRequestStatus status);

    /**
     * 여러 사용자의 진행 중인 요청을 한 번에. 5초 주기 대기 상태 push 가 쓴다 (T2-3).
     *
     * <p>1인 1건이므로 사용자당 최대 한 건이지만, 부르는 쪽은 사용자별로 가장 최근 것만 고른다 —
     * {@link #findFirstByUserIdAndStatusInOrderByCreatedAtDesc} 와 같은 답이 나와야 하기 때문이다.
     */
    List<RideRequest> findByUserIdInAndStatusIn(
            Collection<Long> userIds, Collection<RideRequestStatus> statuses);

    /**
     * 여러 거점의 대기 인원을 한 번에 센다 (T2-3). 대기 인원이 0 인 거점은 결과에 없다.
     *
     * <p>{@link #countByHubIdAndStatus} 를 사람마다 부르면 같은 거점을 수십 번 세게 된다.
     */
    @Query(
            "select new com.gachiga.ride.HubWaitingCount(r.hubId, count(r)) from RideRequest r "
                    + "where r.status = com.gachiga.ride.RideRequestStatus.WAITING "
                    + "and r.hubId in :hubIds group by r.hubId")
    List<HubWaitingCount> countWaitingByHubIds(@Param("hubIds") Collection<Long> hubIds);

    /** 특정 거점의 대기 중 요청. 대기열을 다시 만들 때 쓴다 */
    List<RideRequest> findByHubIdAndStatusOrderByDepartAtAsc(Long hubId, RideRequestStatus status);

    /**
     * WAITING → MATCHED 를 <b>한 문장으로</b> 바꾼다. 중복 배정을 막는 지점이다 (E-02).
     *
     * <p>조건에 {@code version} 과 {@code status} 를 함께 넣어, 두 그룹이 같은 요청을 동시에
     * 집어 가려 하면 나중 쪽이 0건을 갱신하게 만든다. 읽고 나서 쓰는 방식이면 그 사이에
     * 끼어들 틈이 생기므로 하나의 UPDATE 로 처리한다.
     *
     * <p>{@code version} 을 직접 올리는 이유는 이 갱신도 낙관적 락의 한 단계로 세어야
     * 같은 버전으로 두 번 성공하지 않기 때문이다.
     *
     * @return 갱신된 행 수. 1이면 이번 호출이 배정에 성공한 것, 0이면 남이 먼저 가져간 것
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update RideRequest r set r.status = com.gachiga.ride.RideRequestStatus.MATCHED, "
                    + "r.version = r.version + 1 "
                    + "where r.id = :requestId and r.version = :version "
                    + "and r.status = com.gachiga.ride.RideRequestStatus.WAITING")
    int markMatchedIfVersionMatches(
            @Param("requestId") Long requestId, @Param("version") int version);
}
