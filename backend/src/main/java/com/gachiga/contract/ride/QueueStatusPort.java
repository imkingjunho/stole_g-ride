package com.gachiga.contract.ride;

import java.util.Optional;

/**
 * 대기 상태 조회. 구현은 {@code ride/} (이승민), 사용은 임승현 {@code realtime/} 이다.
 * 대기 화면에 5초마다 push 하는 데 쓴다.
 */
public interface QueueStatusPort {

    /**
     * 사용자의 진행 중인 요청 상태.
     *
     * @param userId 대상 사용자 id
     * @return 진행 중인 요청의 상태. 진행 중인 것이 없으면 {@link Optional#empty()}. null 아님
     */
    Optional<QueueStatus> statusOf(Long userId);
}
