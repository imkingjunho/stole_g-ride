package com.gachiga.matching.port;

import java.time.LocalDateTime;

/** 이력 조회 전용 프로젝션. */
public interface HistoryRow {
    Long getGroupId();

    LocalDateTime getRidedAt();

    String getHubName();

    String getDestName();

    Long getMemberCount();

    Integer getShareAmount();

    Integer getSoloFare();

    Integer getSavingAmount();
}
