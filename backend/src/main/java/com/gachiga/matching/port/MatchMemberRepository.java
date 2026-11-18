package com.gachiga.matching.port;

import com.gachiga.matching.domain.MatchMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchMemberRepository extends JpaRepository<MatchMember, Long> {

    List<MatchMember> findByGroupId(Long groupId);

    Optional<MatchMember> findByGroupIdAndUserId(Long groupId, Long userId);

    @Query("SELECT m FROM MatchMember m WHERE m.groupId = :groupId")
    List<MatchMember> findMembersByGroupId(@Param("groupId") Long groupId);

    List<MatchMember> findByUserId(Long userId);

    @Query(
            value =
                    """
        select g.id as groupId, coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt) as ridedAt,
          g.hubName as hubName, m.destName as destName,
          (select count(other) from MatchMember other where other.groupId=g.id) as memberCount,
          m.shareAmount as shareAmount, coalesce(m.soloFare,0) as soloFare, m.savingAmount as savingAmount
        from MatchMember m join MatchGroup g on m.groupId=g.id
        where m.userId=:userId and (m.completed=true or g.status=com.gachiga.matching.domain.GroupStatus.COMPLETED)
          and (:from is null or coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt)>=:from)
          and (:until is null or coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt)<:until)
        order by coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt) desc, g.id desc
        """,
            countQuery =
                    """
        select count(m) from MatchMember m join MatchGroup g on m.groupId=g.id
        where m.userId=:userId and (m.completed=true or g.status=com.gachiga.matching.domain.GroupStatus.COMPLETED)
          and (:from is null or coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt)>=:from)
          and (:until is null or coalesce(m.completedAt,g.closedAt,g.confirmedAt,g.createdAt)<:until)
        """)
    org.springframework.data.domain.Page<HistoryRow> history(
            @Param("userId") Long userId,
            @Param("from") java.time.LocalDateTime from,
            @Param("until") java.time.LocalDateTime until,
            org.springframework.data.domain.Pageable pageable);

    @Query(
            """
        select count(m) as totalRides, coalesce(sum(m.savingAmount),0) as totalSaving,
          coalesce(avg(case when m.soloFare>0 then 1.0*m.savingAmount/m.soloFare else 0.0 end),0.0) as averageSavingRate
        from MatchMember m join MatchGroup g on m.groupId=g.id
        where m.userId=:userId and (m.completed=true or g.status=com.gachiga.matching.domain.GroupStatus.COMPLETED)
        """)
    StatsRow stats(@Param("userId") Long userId);

    @Query(
            """
        select g.hubName from MatchMember m join MatchGroup g on m.groupId=g.id
        where m.userId=:userId and (m.completed=true or g.status=com.gachiga.matching.domain.GroupStatus.COMPLETED)
        group by g.hubName order by count(m) desc, g.hubName asc
        """)
    List<String> mostUsedHubs(
            @Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
