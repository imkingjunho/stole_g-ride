package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachiga.contract.ride.QueueStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link RideRequestAdapter#statusOfAll} 검증 (T2-3) — 실제 DB(H2)에서 쿼리 수를 센다.
 *
 * <p>두 가지를 본다. 사람마다 {@code statusOf} 를 부른 것과 <b>같은 값</b>이 나오는지, 그리고 연결된
 * 인원과 상관없이 <b>쿼리가 두 개</b>인지. 5초마다 도는 대기 상태 push 가 사람 수만큼 쿼리를 내던
 * 것을 고친 것이므로, 숫자로 확인하지 않으면 고쳤다고 말할 수 없다.
 */
@ActiveProfiles("test")
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({RideRequestAdapter.class, RideCandidateCounter.class, QueueStatusBatchTest.Beans.class})
class QueueStatusBatchTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 8, 0);

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }

        @Bean
        RideProperties rideProperties() {
            return new RideProperties(
                    List.of(5, 10, 15, 20),
                    10,
                    List.of(new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.30")),
                    500,
                    30,
                    30);
        }
    }

    @Autowired private RideRequestAdapter adapter;
    @Autowired private RideRequestRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private RideRequest save(Long userId, Long hubId, int minutesAgo) {
        LocalDateTime createdAt = NOW.minusMinutes(minutesAgo);
        return repository.save(
                RideRequest.create(
                        userId,
                        hubId,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        createdAt.plusMinutes(5),
                        20,
                        false,
                        new BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        createdAt));
    }

    @Test
    @DisplayName("사람마다 statusOf 를 부른 것과 값이 같다 — 대기·매칭·요청 없음, 거점 여러 곳")
    void sameAsPerUser() {
        save(1L, 2L, 3); // 거점 2, 대기
        save(2L, 2L, 2); // 거점 2, 대기
        RideRequest matched = save(3L, 2L, 1); // 거점 2, 매칭됨 — 대기 인원에서 빠진다
        matched.markMatched();
        save(4L, 5L, 4); // 거점 5, 혼자 대기
        RideRequest done = save(5L, 5L, 6); // 끝난 요청만 있는 사용자
        done.cancel();
        RideRequest confirmed = save(7L, 5L, 2); // 거점 5, 확정됨
        confirmed.markMatched();
        confirmed.markConfirmed();
        entityManager.flush();
        entityManager.clear();

        List<Long> users = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        Map<Long, QueueStatus> batch = adapter.statusOfAll(users);

        for (Long userId : users) {
            Optional<QueueStatus> single = adapter.statusOf(userId);
            assertThat(Optional.ofNullable(batch.get(userId)))
                    .as("userId=%d", userId)
                    .isEqualTo(single);
        }
        // 거점 2 대기자는 1·2번. 1번 입장에서 나를 뺀 1명, 매칭된 3번은 대기 인원 2명 그대로
        assertThat(batch.get(1L).candidateCount()).isEqualTo(1);
        assertThat(batch.get(3L).candidateCount()).isEqualTo(2);
        assertThat(batch.get(4L).candidateCount()).isZero();
        assertThat(batch).doesNotContainKeys(5L, 6L);
        assertThat(batch.get(7L).status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("500명을 넘으면 사용자 조회를 나눠 하되 한 명도 빠뜨리지 않는다")
    void chunksAboveLimit() {
        List<Long> userIds = new ArrayList<>();
        for (long u = 1; u <= 1_001; u++) {
            save(u, 1 + (u % 6), 1);
            userIds.add(u);
        }
        entityManager.flush();
        entityManager.clear();

        statistics.clear();
        Map<Long, QueueStatus> batch = adapter.statusOfAll(userIds);

        assertThat(batch).hasSize(1_001).containsKeys(1L, 500L, 501L, 1_000L, 1_001L);
        // 사용자 1,001명 → 3조각, 거점 6곳 → 1조각
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("조각 나누기: 마지막 조각은 짧고, 합치면 원래 목록이다")
    void chunkBoundaries() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        List<List<Integer>> chunks = RideRequestAdapter.chunks(items, 2);

        assertThat(chunks).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(RideRequestAdapter.chunks(List.of(), 2)).isEmpty();
    }

    @ParameterizedTest(name = "연결 {0}명")
    @ValueSource(ints = {1, 10, 50, 200})
    @DisplayName("인원과 상관없이 쿼리는 두 개다 — 사람마다 부르면 두 배씩 늘어난다")
    void constantQueryCount(int users) {
        List<Long> userIds = new ArrayList<>();
        for (long u = 1; u <= users; u++) {
            save(u, 1 + (u % 6), 1);
            userIds.add(u);
        }
        entityManager.flush();
        entityManager.clear();

        statistics.clear();
        Map<Long, QueueStatus> batch = adapter.statusOfAll(userIds);
        long batchQueries = statistics.getPrepareStatementCount();

        statistics.clear();
        userIds.forEach(adapter::statusOf);
        long perUserQueries = statistics.getPrepareStatementCount();

        assertThat(batch).hasSize(users);
        assertThat(batchQueries).isEqualTo(2);
        assertThat(perUserQueries).isEqualTo(2L * users);
    }
}
