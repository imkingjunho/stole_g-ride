package com.gachiga.contract.ride;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 대기 상태 조회. 구현은 {@code ride/} (이승민), 사용은 임승현 {@code realtime/} 이다.
 * 대기 화면에 5초마다 push 하는 데 쓴다.
 */
public interface QueueStatusPort {

    /**
     * 사용자의 진행 중인 요청 상태.
     *
     * <p>이벤트를 받아 <b>한 사람에게</b> 바로 push 할 때 쓴다. 연결된 사용자 전원을 주기적으로 돌 때는
     * {@link #statusOfAll} 을 쓴다 — 이 메서드를 사람마다 부르면 쿼리가 사람 수만큼 나간다.
     *
     * @param userId 대상 사용자 id
     * @return 진행 중인 요청의 상태. 진행 중인 것이 없으면 {@link Optional#empty()}. null 아님
     */
    Optional<QueueStatus> statusOf(Long userId);

    /**
     * 여러 사용자의 진행 중인 요청 상태를 한 번에 (T2-3).
     *
     * <p>5초 주기 push 가 연결된 사용자 전원을 조회할 때 쓴다. 쿼리는 500명까지 두 개이고, 넘으면 500명
     * 단위로 나뉜다(사람마다 {@link #statusOf} 를 부르면 사람 수 × 2). 값은 사람마다 부른 결과와 같다.
     *
     * @param userIds 대상 사용자 id. 비어 있으면 쿼리 없이 빈 맵을 돌려준다. 중복은 한 번만 센다
     * @return 사용자 id → 상태. <b>진행 중인 요청이 있는 사용자만 들어 있다</b> — 키가 없으면
     *     {@link #statusOf} 가 빈 값을 주는 경우와 같다. null 아님
     */
    Map<Long, QueueStatus> statusOfAll(Collection<Long> userIds);
}
