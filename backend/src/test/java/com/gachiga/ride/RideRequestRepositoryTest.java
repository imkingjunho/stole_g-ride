package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 리포지토리를 <b>실제 DB(임베디드 H2)</b>로 검증한다.
 *
 * <p>Mock 으로는 확인할 수 없는 두 가지가 목적이다.
 *
 * <ul>
 *   <li>조건부 UPDATE 가 정말 원자적으로 동작하는지 — 중복 배정 차단 (E-02)
 *   <li>진행 중 사용자 유니크 제약이 실제로 두 번째 INSERT 를 막는지 — 1인 1건 (FR-08)
 * </ul>
 *
 * <p>H2 와 MySQL 은 다르므로 여기 통과가 MySQL 통과를 뜻하지는 않는다. 다만 제약과
 * 쿼리 의미는 같으므로, 여기서 깨지면 MySQL 에서도 깨진다.
 */
@ActiveProfiles("test")
@DataJpaTest
class RideRequestRepositoryTest {

    @Autowired private RideRequestRepository repository;
    @Autowired private EntityManager entityManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);

    private RideRequest newRequest(Long userId, Long hubId, LocalDateTime departAt) {
        return RideRequest.create(
                userId,
                hubId,
                "광주송정역",
                35.1378d,
                126.7902d,
                departAt,
                10,
                false,
                new BigDecimal("0.20"),
                15_466,
                15_300,
                true,
                NOW);
    }

    @Test
    @DisplayName("배정은 한 번만 성공한다 — 같은 버전으로 두 번째는 0건 (E-02)")
    void matchOnlyOnce() {
        RideRequest saved = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));
        entityManager.clear();

        assertThat(repository.markMatchedIfVersionMatches(saved.getId(), 0)).isEqualTo(1);
        assertThat(repository.markMatchedIfVersionMatches(saved.getId(), 0)).isZero();
    }

    @Test
    @DisplayName("배정에 성공하면 상태와 버전이 함께 올라간다")
    void matchBumpsStatusAndVersion() {
        RideRequest saved = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));
        Long id = saved.getId();
        entityManager.clear();

        repository.markMatchedIfVersionMatches(id, 0);
        entityManager.clear();

        RideRequest reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RideRequestStatus.MATCHED);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("대기 중이 아니면 배정되지 않는다")
    void doesNotMatchNonWaiting() {
        RideRequest saved = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));
        saved.cancel();
        repository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(repository.markMatchedIfVersionMatches(saved.getId(), saved.getVersion()))
                .isZero();
    }

    @Test
    @DisplayName("같은 사용자의 진행 중 요청은 DB 가 하나로 막는다 (FR-08)")
    void oneActiveRequestPerUser() {
        repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));

        assertThatThrownBy(
                        () -> repository.saveAndFlush(newRequest(1L, 5L, NOW.plusMinutes(10))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("끝난 요청은 몇 건이든 남아도 된다 — 취소 후 다시 요청할 수 있다")
    void finishedRequestsDoNotBlockNewOnes() {
        RideRequest first = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));
        first.cancel();
        repository.saveAndFlush(first);

        RideRequest second = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(10)));
        second.expire();
        repository.saveAndFlush(second);

        RideRequest third = repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(15)));

        assertThat(third.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 사용자끼리는 서로 막지 않는다")
    void differentUsersCoexist() {
        repository.saveAndFlush(newRequest(1L, 2L, NOW.plusMinutes(5)));
        repository.saveAndFlush(newRequest(2L, 2L, NOW.plusMinutes(5)));

        assertThat(repository.countByHubIdAndStatus(2L, RideRequestStatus.WAITING)).isEqualTo(2);
    }

    @Test
    @DisplayName("매칭 대상 조회는 만료 기준 시각을 넘긴 것만 준다 (E-04)")
    void findWaitingRespectsCutoff() {
        RideRequest soon = newRequest(1L, 2L, NOW.plusMinutes(5));
        org.springframework.test.util.ReflectionTestUtils.setField(
                soon, "expiresAt", NOW.plusSeconds(20));
        repository.saveAndFlush(soon);

        RideRequest later = newRequest(2L, 2L, NOW.plusMinutes(5));
        org.springframework.test.util.ReflectionTestUtils.setField(
                later, "expiresAt", NOW.plusMinutes(10));
        repository.saveAndFlush(later);

        List<RideRequest> found =
                repository.findByStatusAndExpiresAtAfterOrderByDepartAtAsc(
                        RideRequestStatus.WAITING, NOW.plusSeconds(30));

        assertThat(found).extracting(RideRequest::getUserId).containsExactly(2L);
    }
}
