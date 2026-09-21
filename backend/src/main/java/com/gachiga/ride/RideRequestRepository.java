package com.gachiga.ride;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** 특정 거점의 대기 중 요청. 대기열을 다시 만들 때 쓴다 */
    List<RideRequest> findByHubIdAndStatusOrderByDepartAtAsc(Long hubId, RideRequestStatus status);
}
