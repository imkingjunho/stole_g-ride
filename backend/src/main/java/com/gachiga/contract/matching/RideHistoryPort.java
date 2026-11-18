package com.gachiga.contract.matching;

import java.time.LocalDateTime;
import java.util.List;

/** matching이 소유한 완료 내역을 stats가 읽는다. 다른 모듈의 엔티티를 공유하지 않는다. */
public interface RideHistoryPort {
    HistoryPage history(Long userId, LocalDateTime from, LocalDateTime until, int page, int size);

    MyStats stats(Long userId);

    record HistoryItem(
            Long groupId,
            LocalDateTime ridedAt,
            String hubName,
            String destName,
            long memberCount,
            int shareAmount,
            int soloFare,
            int savingAmount) {}

    record HistoryPage(
            List<HistoryItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last) {}

    record MyStats(
            long totalRides, long totalSaving, double averageSavingRate, String mostUsedHubName) {}
}
