package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.contract.matching.MatchOperationsPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TreeMap;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long>, MatchOperationsPort {

    @Query("""
            select distinct g.pairOfferKey from MatchGroup g join MatchMember m on m.groupId=g.id
            where g.pairOfferKey is not null and m.requestId in :requestIds
            """)
    java.util.Set<String> offeredPairs(@Param("requestIds") List<Long> requestIds);

    @Query("select g.id from MatchGroup g where g.status = com.gachiga.matching.domain.GroupStatus.PENDING and g.acceptDeadline <= :now")
    List<Long> expiredOffers(@Param("now") LocalDateTime now);

    @Override
    @Query("""
            select new com.gachiga.contract.matching.MatchOperationsPort$Member(
                m.userId, g.id, g.hubName,
                case when g.status = com.gachiga.matching.domain.GroupStatus.CONFIRMED then true else false end)
            from MatchGroup g join MatchMember m on m.groupId=g.id
            where g.status in (com.gachiga.matching.domain.GroupStatus.PENDING,
                               com.gachiga.matching.domain.GroupStatus.CONFIRMED)
              and coalesce(m.completed, false) = false
            order by g.id desc, m.userId
            """)
    List<MatchOperationsPort.Member> currentMembers();

    @Query("""
            select g.confirmedAt from MatchGroup g
            where g.confirmedAt >= :from and g.confirmedAt < :until
            """)
    List<LocalDateTime> confirmationTimes(
            @Param("from") LocalDateTime from, @Param("until") LocalDateTime until);

    @Override
    default List<MatchOperationsPort.Day> confirmedBetween(LocalDateTime from, LocalDateTime until) {
        // 날짜로 자르기 전에 엔티티 조회와 같은 JDBC 시간대 변환을 거친다.
        var days = new TreeMap<LocalDate, Long>();
        for (var confirmedAt : confirmationTimes(from, until)) {
            days.merge(confirmedAt.toLocalDate(), 1L, Long::sum);
        }
        return days.entrySet().stream()
                .map(day -> new MatchOperationsPort.Day(day.getKey(), day.getValue()))
                .toList();
    }

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from MatchGroup g where g.id = :id")
    java.util.Optional<MatchGroup> findLockedById(
            @org.springframework.data.repository.query.Param("id") Long id);

    @Query("SELECT g FROM MatchGroup g WHERE g.status = 'PENDING' ORDER BY g.createdAt DESC")
    List<MatchGroup> findRecentPending();
}
