package com.gachiga.stats;

import com.gachiga.contract.matching.MatchOperationsPort;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboard(
        LocalDateTime asOf,
        List<Waiting> waiting,
        List<Member> matched,
        List<MatchOperationsPort.Day> daily,
        List<Ended> endedByHub) {
    public record Ended(String hubName, long expired, long cancelled) {}

    public record Waiting(String nickname, String hubName, LocalDateTime requestedAt,
                          LocalDateTime expiresAt, Integer preferredSize) {}

    public record Member(String nickname, String hubName, Long groupId, boolean confirmed) {}
}
