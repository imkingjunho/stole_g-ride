package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchGroup;
import com.gachiga.contract.matching.MatchOperationsPort;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long>, MatchOperationsPort {

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

    @Override
    @Query("""
            select new com.gachiga.contract.matching.MatchOperationsPort$Day(
                cast(g.confirmedAt as LocalDate), count(g))
            from MatchGroup g where g.confirmedAt >= :from and g.confirmedAt < :until
            group by cast(g.confirmedAt as LocalDate)
            """)
    List<MatchOperationsPort.Day> confirmedBetween(
            @Param("from") LocalDateTime from, @Param("until") LocalDateTime until);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from MatchGroup g where g.id = :id")
    java.util.Optional<MatchGroup> findLockedById(
            @org.springframework.data.repository.query.Param("id") Long id);

    @Query("SELECT g FROM MatchGroup g WHERE g.status = 'PENDING' ORDER BY g.createdAt DESC")
    List<MatchGroup> findRecentPending();
}
