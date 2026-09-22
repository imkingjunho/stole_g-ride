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

    @Query("SELECT m FROM MatchMember m WHERE m.group.id = :groupId")
    List<MatchMember> findMembersByGroupId(@Param("groupId") Long groupId);

    List<MatchMember> findByUserId(Long userId);
}