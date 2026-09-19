package com.gachiga.ride;

import com.gachiga.contract.ride.QueueStatus;
import com.gachiga.contract.ride.QueueStatusPort;
import com.gachiga.contract.ride.RideRequestPort;
import com.gachiga.contract.ride.WaitingRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * ⚠️ <b>Phase 0 스텁</b> — 매칭 요청을 메모리에 담아 두는 임시 구현 (PRD §14.7).
 *
 * <p>아직 요청 생성 API 가 없으므로 평소에는 비어 있다. 서준이 매칭 엔진을, 임승현이 대기 화면을
 * 먼저 붙여 볼 수 있게 <b>인터페이스만 채워 두는</b> 것이 목적이다.
 *
 * <p>상태 전이와 낙관적 락(E-02)은 실제와 같은 규칙으로 흉내 낸다. 다만 서버를 끄면 다 사라지고,
 * 서버가 여러 대면 공유되지 않는다.
 *
 * <p><b>교체 담당: 이승민 · Phase 1 (T1-5·T1-6).</b> JPA + Redis Sorted Set 구현으로 바꾸고
 * 이 클래스를 삭제한다.
 */
@Component
public class InMemoryRideRequestAdapter implements RideRequestPort, QueueStatusPort {

    /** 만료가 이만큼도 안 남은 요청은 새 그룹에 넣지 않는다 (E-04) */
    private static final Duration MATCH_CUTOFF = Duration.ofSeconds(30);

    private static final String WAITING = "WAITING";
    private static final String MATCHED = "MATCHED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String COMPLETED = "COMPLETED";

    /** requestId → 요청. 여러 스레드(매칭 tick·API·스케줄러)가 동시에 건드린다 */
    private final Map<Long, Entry> store = new ConcurrentHashMap<>();

    /**
     * 데모·수동 시험용으로 요청을 하나 넣는다. Phase 0 에만 쓰는 통로이며
     * {@link RideRequestPort} 계약에는 없다.
     */
    public void put(WaitingRequest request) {
        store.put(request.requestId(), new Entry(request, WAITING));
    }

    @Override
    public List<WaitingRequest> findWaiting() {
        LocalDateTime cutoff = LocalDateTime.now().plus(MATCH_CUTOFF);
        List<WaitingRequest> result = new ArrayList<>();
        for (Entry entry : store.values()) {
            if (WAITING.equals(entry.status) && entry.request.expiresAt().isAfter(cutoff)) {
                result.add(entry.request);
            }
        }
        // 실구현(Redis Sorted Set)과 같게 희망 출발 시각 순으로 돌려준다
        result.sort(Comparator.comparing(WaitingRequest::departAt));
        return result;
    }

    /**
     * WAITING → MATCHED. 두 그룹이 같은 요청을 동시에 집어 가면 나중 쪽이 false 를 받는다 (E-02).
     *
     * <p>{@code compute} 안에서 검사와 변경을 함께 해 그 사이에 끼어들 틈을 없앤다.
     */
    @Override
    public boolean tryMarkMatched(Long requestId, int version) {
        // 바뀐 결과만 보면 "내가 방금 바꿨다"와 "남이 이미 바꿔 놨다"를 구분할 수 없다.
        // 람다 안에서 성공 여부를 직접 기록한다.
        AtomicBoolean matched = new AtomicBoolean(false);
        store.computeIfPresent(
                requestId,
                (id, entry) -> {
                    if (!WAITING.equals(entry.status) || entry.request.version() != version) {
                        return entry; // 이미 남이 가져갔다. 그대로 둔다
                    }
                    matched.set(true);
                    // 버전을 올려 같은 버전으로 두 번 성공하지 못하게 한다
                    return new Entry(withVersion(entry.request, version + 1), MATCHED);
                });
        return matched.get();
    }

    @Override
    public void markWaiting(Long requestId) {
        changeStatus(requestId, WAITING);
    }

    @Override
    public void markConfirmed(Long requestId) {
        changeStatus(requestId, CONFIRMED);
    }

    @Override
    public void markCompleted(Long requestId) {
        changeStatus(requestId, COMPLETED);
    }

    @Override
    public Optional<QueueStatus> statusOf(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return store.values().stream()
                .filter(entry -> entry.request.userId().equals(userId))
                .filter(entry -> !COMPLETED.equals(entry.status))
                .findFirst()
                .map(entry -> toQueueStatus(entry, now));
    }

    private QueueStatus toQueueStatus(Entry entry, LocalDateTime now) {
        long remaining = Duration.between(now, entry.request.expiresAt()).toSeconds();
        int candidates = countSameHubWaiting(entry.request);
        return new QueueStatus(
                entry.request.requestId(),
                entry.status,
                (int) Math.max(0, remaining),
                candidates);
    }

    /** 같은 거점에서 함께 기다리는 사람 수 (나 자신 제외) */
    private int countSameHubWaiting(WaitingRequest mine) {
        int count = 0;
        for (Entry other : store.values()) {
            if (WAITING.equals(other.status)
                    && other.request.hubId().equals(mine.hubId())
                    && !other.request.requestId().equals(mine.requestId())) {
                count++;
            }
        }
        return count;
    }

    private void changeStatus(Long requestId, String status) {
        store.computeIfPresent(requestId, (id, entry) -> new Entry(entry.request, status));
    }

    /** record 는 값을 바꿀 수 없으므로 버전만 바꾼 새 값을 만든다 */
    private WaitingRequest withVersion(WaitingRequest r, int version) {
        return new WaitingRequest(
                r.requestId(),
                r.userId(),
                r.hubId(),
                r.destination(),
                r.destName(),
                r.departAt(),
                r.expiresAt(),
                r.createdAt(),
                r.sameGenderOnly(),
                r.maxDetourRatio(),
                r.soloDistance(),
                r.soloFare(),
                version);
    }

    /** 요청 + 현재 상태. {@link WaitingRequest} 에는 상태가 없어 여기서 함께 들고 있는다 */
    private record Entry(WaitingRequest request, String status) {}
}
