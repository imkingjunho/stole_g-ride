package com.gachiga.contract.matching;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 운영 통계는 그룹 단위로 확정 시점에 한 번 집계한다. */
public interface MatchOperationsPort {
    record Member(Long userId, Long groupId, String hubName, boolean confirmed) {}

    record Day(LocalDate date, long groups) {}

    List<Member> currentMembers();

    List<Day> confirmedBetween(LocalDateTime from, LocalDateTime until);
}
