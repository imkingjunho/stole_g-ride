package com.gachiga.contract.ride;

import java.time.LocalDateTime;
import java.util.List;

/** 운영 화면용 현재 대기자. 오래 기다린 요청부터, 만료된 요청은 제외한다. */
public interface WaitingRosterPort {
    record Entry(Long userId, Long hubId, LocalDateTime requestedAt) {}

    List<Entry> waitingAt(LocalDateTime now);
}
