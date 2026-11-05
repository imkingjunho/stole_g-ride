package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * {@link RideQueue} 검증.
 *
 * <p>Redis 서버 없이 돈다 — {@link StringRedisTemplate} 을 Mock 으로 둔다 (CLAUDE.md §7).
 * 가장 중요한 성질은 <b>Redis 가 죽어도 예외가 밖으로 새지 않는다</b>는 것이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideQueueTest {

    private static final Long HUB_ID = 2L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSet;

    private RideQueue queue;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForZSet()).willReturn(zSet);
        queue = new RideQueue(redisTemplate);
    }

    @Test
    @DisplayName("키는 queue:{hubId} 이고 점수는 희망 출발 시각의 epoch 초다")
    void addsWithDepartAtAsScore() {
        LocalDateTime departAt = LocalDateTime.of(2026, 10, 20, 8, 30);

        queue.add(HUB_ID, 7L, departAt);

        verify(zSet).add("queue:2", "7", (double) departAt.atZone(SEOUL).toEpochSecond());
    }

    @Test
    @DisplayName("점수가 출발 시각이라 별도 정렬 없이 먼저 출발할 사람이 앞에 온다")
    void scoreOrdersByDepartTime() {
        LocalDateTime earlier = LocalDateTime.of(2026, 10, 20, 8, 30);
        LocalDateTime later = LocalDateTime.of(2026, 10, 20, 8, 45);

        assertThat(earlier.atZone(SEOUL).toEpochSecond())
                .isLessThan(later.atZone(SEOUL).toEpochSecond());
    }

    @Test
    @DisplayName("제거하면 해당 요청 id 만 빠진다")
    void removesMember() {
        queue.remove(HUB_ID, 7L);

        verify(zSet).remove("queue:2", "7");
    }

    @Test
    @DisplayName("조회하면 요청 id 를 저장 순서 그대로 돌려준다")
    void readsRequestIdsInOrder() {
        given(zSet.range("queue:2", 0, -1))
                .willReturn(new LinkedHashSet<>(List.of("3", "1", "9")));

        assertThat(queue.requestIds(HUB_ID)).containsExactly(3L, 1L, 9L);
    }

    @Test
    @DisplayName("대기열이 비어 있으면 빈 목록이다")
    void emptyQueueGivesEmptyList() {
        given(zSet.range("queue:2", 0, -1)).willReturn(new LinkedHashSet<>());

        assertThat(queue.requestIds(HUB_ID)).isEmpty();
    }

    @Test
    @DisplayName("Redis 가 죽어도 등록이 예외를 던지지 않는다 — 요청 생성을 막으면 안 된다")
    void addSurvivesRedisFailure() {
        willThrow(new QueryTimeoutException("redis down"))
                .given(zSet)
                .add(anyString(), anyString(), anyDouble());

        assertThatCode(() -> queue.add(HUB_ID, 7L, LocalDateTime.now())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 가 죽어도 조회는 빈 목록을 준다 — 호출 측이 DB 로 대체한다")
    void readSurvivesRedisFailure() {
        given(zSet.range("queue:2", 0, -1)).willThrow(new QueryTimeoutException("redis down"));

        assertThat(queue.requestIds(HUB_ID)).isEmpty();
    }

    @Test
    @DisplayName("Redis 가 죽어도 크기 조회는 0을 준다")
    void sizeSurvivesRedisFailure() {
        given(zSet.size(anyString())).willThrow(new QueryTimeoutException("redis down"));

        assertThat(queue.size(HUB_ID)).isZero();
    }

    @Test
    @DisplayName("재구성은 기존 키를 지우고 DB 기준으로 다시 채운다")
    void rebuildClearsThenRefills() {
        RideRequest first = request(1L, LocalDateTime.of(2026, 10, 20, 8, 30));
        RideRequest second = request(2L, LocalDateTime.of(2026, 10, 20, 8, 40));

        queue.rebuild(HUB_ID, List.of(first, second));

        verify(redisTemplate).delete("queue:2");
        verify(zSet).add("queue:2", "1", (double) first.getDepartAt().atZone(SEOUL).toEpochSecond());
        verify(zSet).add("queue:2", "2", (double) second.getDepartAt().atZone(SEOUL).toEpochSecond());
    }

    private RideRequest request(Long id, LocalDateTime departAt) {
        RideRequest request =
                RideRequest.create(
                        1L,
                        HUB_ID,
                        "광주송정역",
                        35.1378d,
                        126.7902d,
                        departAt,
                        10,
                        false,
                        new java.math.BigDecimal("0.20"),
                        15_466,
                        15_300,
                        true,
                        LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}
