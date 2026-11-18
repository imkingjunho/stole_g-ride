package com.gachiga.stats;

import com.gachiga.contract.matching.MatchOperationsPort;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboard(
        LocalDateTime asOf,
        List<Waiting> waiting,
        List<Member> matched,
        List<MatchOperationsPort.Day> daily) {
    public record Waiting(String nickname, String hubName, LocalDateTime requestedAt) {}

    public record Member(String nickname, String hubName, Long groupId, boolean confirmed) {}
}
