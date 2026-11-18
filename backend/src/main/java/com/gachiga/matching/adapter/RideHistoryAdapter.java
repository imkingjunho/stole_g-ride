package com.gachiga.matching.adapter;

import com.gachiga.contract.matching.RideHistoryPort;
import com.gachiga.matching.port.MatchMemberRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RideHistoryAdapter implements RideHistoryPort {
    private final MatchMemberRepository members;

    public HistoryPage history(
            Long userId, LocalDateTime from, LocalDateTime until, int page, int size) {
        var rows = members.history(userId, from, until, PageRequest.of(page, size));
        return new HistoryPage(
                rows.getContent().stream()
                        .map(
                                r ->
                                        new HistoryItem(
                                                r.getGroupId(),
                                                r.getRidedAt(),
                                                r.getHubName(),
                                                r.getDestName(),
                                                r.getMemberCount(),
                                                r.getShareAmount(),
                                                r.getSoloFare(),
                                                r.getSavingAmount()))
                        .toList(),
                page,
                size,
                rows.getTotalElements(),
                rows.getTotalPages(),
                rows.isLast());
    }

    public MyStats stats(Long userId) {
        var stats = members.stats(userId);
        var hubs = members.mostUsedHubs(userId, PageRequest.of(0, 1));
        return new MyStats(
                stats.getTotalRides(),
                stats.getTotalSaving(),
                stats.getAverageSavingRate(),
                hubs.isEmpty() ? null : hubs.get(0));
    }
}
