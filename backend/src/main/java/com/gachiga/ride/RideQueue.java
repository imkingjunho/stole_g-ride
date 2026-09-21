package com.gachiga.ride;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * 거점별 대기열 (PRD §7.1 Redis 키).
 *
 * <p>키는 {@code queue:{hubId}}, 자료구조는 Sorted Set 이고 <b>점수는 희망 출발 시각</b>이다.
 * 그래서 별도 정렬 없이 "먼저 출발할 사람부터" 꺼낼 수 있다.
 *
 * <p><b>진실의 원천은 DB 다.</b> 이 대기열은 빠르게 훑기 위한 색인일 뿐이므로, 둘이 어긋나면
 * DB 를 믿고 {@link #rebuild} 로 다시 만든다. 그래서 Redis 가 잠깐 죽어도 요청 생성 자체는
 * 실패하지 않아야 한다 — 이 클래스의 메서드는 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideQueue {

    private static final String KEY_PREFIX = "queue:";

    /** 서버 시간대와 무관하게 같은 점수가 나오도록 고정한다 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    /**
     * 대기열에 넣는다. 이미 있으면 점수만 갱신된다.
     *
     * @param departAt 희망 출발 시각. 정렬 점수가 된다
     */
    public void add(Long hubId, Long requestId, LocalDateTime departAt) {
        execute(
                "등록",
                requestId,
                () ->
                        zSet().add(key(hubId), String.valueOf(requestId), toScore(departAt)));
    }

    /** 대기열에서 뺀다. 없어도 조용히 넘어간다 */
    public void remove(Long hubId, Long requestId) {
        execute("제거", requestId, () -> zSet().remove(key(hubId), String.valueOf(requestId)));
    }

    /**
     * 거점의 대기 요청 id 를 희망 출발 시각 순으로 돌려준다.
     *
     * @return 요청 id 목록. Redis 를 읽지 못하면 빈 목록 (호출 측은 DB 로 대체한다)
     */
    public List<Long> requestIds(Long hubId) {
        try {
            Set<String> members = zSet().range(key(hubId), 0, -1);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            return members.stream().map(Long::valueOf).toList();
        } catch (RuntimeException e) {
            log.warn("대기열 조회 실패 hubId={} — DB 로 대체한다", hubId, e);
            return List.of();
        }
    }

    /** 거점의 대기 인원 수. 읽지 못하면 0 */
    public long size(Long hubId) {
        try {
            Long size = zSet().size(key(hubId));
            return size == null ? 0L : size;
        } catch (RuntimeException e) {
            log.warn("대기열 크기 조회 실패 hubId={}", hubId, e);
            return 0L;
        }
    }

    /**
     * DB 를 기준으로 거점 하나의 대기열을 통째로 다시 만든다.
     *
     * <p>Redis 가 재시작됐거나 DB 와 어긋났을 때 쓴다. 기존 키를 지우고 새로 채우므로
     * 중간 상태가 잠깐 보일 수 있지만, 대기열은 색인일 뿐이라 문제되지 않는다.
     */
    public void rebuild(Long hubId, List<RideRequest> waitingRequests) {
        try {
            redisTemplate.delete(key(hubId));
            for (RideRequest request : waitingRequests) {
                zSet().add(
                                key(hubId),
                                String.valueOf(request.getId()),
                                toScore(request.getDepartAt()));
            }
            log.info("대기열 재구성 hubId={} {}건", hubId, waitingRequests.size());
        } catch (RuntimeException e) {
            log.warn("대기열 재구성 실패 hubId={}", hubId, e);
        }
    }

    /** 거점 대기열을 비운다. 테스트와 재구성에 쓴다 */
    public void clear(Long hubId) {
        try {
            redisTemplate.delete(key(hubId));
        } catch (RuntimeException e) {
            log.warn("대기열 삭제 실패 hubId={}", hubId, e);
        }
    }

    private ZSetOperations<String, String> zSet() {
        return redisTemplate.opsForZSet();
    }

    private String key(Long hubId) {
        return KEY_PREFIX + hubId;
    }

    /** 희망 출발 시각을 정렬 점수(epoch 초)로 */
    private double toScore(LocalDateTime departAt) {
        return departAt.atZone(ZONE).toEpochSecond();
    }

    /**
     * Redis 작업을 실행하되 실패해도 밖으로 던지지 않는다.
     *
     * <p>대기열이 잠깐 어긋나는 것보다 요청 자체가 실패하는 쪽이 사용자에게 더 나쁘다.
     * 어긋난 것은 {@link #rebuild} 로 복구한다.
     */
    private void execute(String action, Long requestId, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException e) {
            log.warn("대기열 {} 실패 requestId={} — DB 상태는 정상이다", action, requestId, e);
        }
    }

    /** 테스트에서 비교하기 쉽도록 빈 목록 상수를 노출한다 */
    static List<Long> empty() {
        return Collections.emptyList();
    }
}
