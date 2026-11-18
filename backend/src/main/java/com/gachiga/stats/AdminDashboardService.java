package com.gachiga.stats;

import com.gachiga.contract.matching.MatchOperationsPort;
import com.gachiga.contract.ride.WaitingRosterPort;
import com.gachiga.contract.route.HubInfo;
import com.gachiga.contract.route.HubPort;
import com.gachiga.contract.user.UserPort;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final Clock clock;
    private final WaitingRosterPort queue;
    private final MatchOperationsPort matches;
    private final HubPort hubs;
    private final UserPort users;

    @Transactional(readOnly = true)
    public AdminDashboard snapshot() {
        var now = LocalDateTime.now(clock);
        var start = now.toLocalDate().minusDays(6);
        var end = now.toLocalDate().plusDays(1);
        var counts =
                matches.confirmedBetween(start.atStartOfDay(), end.atStartOfDay()).stream()
                        .collect(
                                Collectors.toMap(
                                        MatchOperationsPort.Day::date,
                                        MatchOperationsPort.Day::groups));
        var daily =
                start.datesUntil(end)
                        .map(
                                date ->
                                        new MatchOperationsPort.Day(
                                                date, counts.getOrDefault(date, 0L)))
                        .toList();
        var waiting = queue.waitingAt(now);
        var matched = matches.currentMembers();
        var ids =
                Stream.concat(
                                waiting.stream().map(WaitingRosterPort.Entry::userId),
                                matched.stream().map(MatchOperationsPort.Member::userId))
                        .collect(Collectors.toSet());
        var names = users.nicknames(ids);
        var hubNames =
                hubs.findAll().stream().collect(Collectors.toMap(HubInfo::id, HubInfo::name));
        return new AdminDashboard(
                now,
                waiting.stream()
                        .map(
                                row ->
                                        new AdminDashboard.Waiting(
                                                names.getOrDefault(row.userId(), "알 수 없는 회원"),
                                                hubNames.getOrDefault(row.hubId(), "알 수 없는 거점"),
                                                row.requestedAt()))
                        .toList(),
                matched.stream()
                        .map(
                                row ->
                                        new AdminDashboard.Member(
                                                names.getOrDefault(row.userId(), "알 수 없는 회원"),
                                                row.hubName() == null ? "알 수 없는 거점" : row.hubName(),
                                                row.groupId(),
                                                row.confirmed()))
                        .toList(),
                daily);
    }
}
