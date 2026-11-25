package com.gachiga.contract.ride;

import java.time.LocalDateTime;
import java.util.List;

/** 운영 화면용 현재 대기자. 오래 기다린 요청부터, 만료된 요청은 제외한다. */
public interface WaitingRosterPort {
    record Entry(Long userId, Long hubId, LocalDateTime requestedAt, LocalDateTime expiresAt, Integer preferredSize) {}

    List<Entry> waitingAt(LocalDateTime now);

    record Ended(Long hubId, long expired, long cancelled) {}

    /** 해당 기간에 생성된 요청 중 현재 만료 또는 취소 상태인 건수를 거점별로 집계한다. */
    List<Ended> endedByHub(LocalDateTime start, LocalDateTime asOf);
}
